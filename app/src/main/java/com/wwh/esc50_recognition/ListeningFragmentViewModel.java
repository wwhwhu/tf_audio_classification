package com.wwh.esc50_recognition;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineExceptionHandler;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

// 测试电量
// adb tcpip 5555
// adb connect 192.168.1.50
// adb shell dumpsys batterystats --reset
// adb shell dumpsys batterystats|findstr u0a239
// adb disconnect

public class ListeningFragmentViewModel extends AndroidViewModel {

    private ListeningRecorder listeningRecorderObject;
    private ModelExecutor esc50_model_exe;
    public boolean listeningRunning = false;
    private final Context context;

    private final Handler updateLoopListeningHandler = new Handler(Looper.getMainLooper());
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler handlerStart = new Handler(Looper.getMainLooper());

    private final MutableLiveData<Boolean> _inferenceDone = new MutableLiveData<>();
    public LiveData<Boolean> inferenceDone = _inferenceDone;

    private final MutableLiveData<Boolean> _listeningEnd = new MutableLiveData<>(Boolean.TRUE);
    public LiveData<Boolean> listeningEnd = _listeningEnd;

    private final MutableLiveData<Pair<ArrayList<String>, ArrayList<Float>>> _listOfClasses = new MutableLiveData<>();
    public LiveData<Pair<ArrayList<String>, ArrayList<Float>>> listOfClasses = _listOfClasses;

    public ListeningFragmentViewModel(@NotNull Application application) throws IOException {
        super(application);
        context = application;
        // Log.d("Info_ESC",Boolean.toString(context==null)); false
        // Dependency Injection (DI)
        listeningRecorderObject = new ListeningRecorder("hotkey", 2, application);
        esc50_model_exe = new ModelExecutor(context, true);
    }

    public void startListening() {
        listeningRunning = true;
        listeningRecorderObject.startRecording();
    }

    public void stopListening() {
        ByteArrayOutputStream stream = listeningRecorderObject.stopRecording();
        ArrayList<Short> streamForInference = listeningRecorderObject.stopRecordingForInference();
        // 获取原始ArrayList中的子列表
        int endIndex = Math.min(160000, streamForInference.size());
        ArrayList<Short> sublist = new ArrayList<>();
        for (int i = 0; i < endIndex; i++) {
            sublist.add(streamForInference.get(i));
        }
        // 补零
        while (sublist.size() < 160000) {
            sublist.add((short) 0);
        }
        Log.d("Info_ESC50", "推理音频size：" + String.valueOf(sublist.size()));
        // 获取最后100个元素
        // Log.d("Info_ESC50", "推理音频value示例：" + streamForInference.subList(Math.max(0, streamForInference.size() - 100), streamForInference.size()).toString());
        ByteArrayOutputStream stream2 = getFirstNBytes(stream, 320000);
        listeningRunning = false;
        // Background thread to do inference with the generated short array list
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                doInference(stream2, sublist);
            }
        });
        thread.start();
    }

    private ByteArrayOutputStream getFirstNBytes(ByteArrayOutputStream originalStream, int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("N must be a positive number");
        }
        byte[] originalBytes = originalStream.toByteArray(); // 获取整个字节数组
        ByteArrayOutputStream newStream = new ByteArrayOutputStream();
        newStream.write(originalBytes, 0, Math.min(n, originalBytes.length));
        return newStream;
    }

    private void doInference(ByteArrayOutputStream stream, ArrayList<Short> arrayListShorts) {
        // Write .wav file to external directory
        listeningRecorderObject.writeWav(stream);
        // Reset stream
        listeningRecorderObject.reInitializePcmStream();
        Log.d("Info_ESC50", "准备推理：正在归一化");
        // The input must be normalized to floats between -1 and 1.
        // To normalize it, we just need to divide all the values by 2**16 or in our code, MAX_ABS_INT16 = 32768
        float[] floatsForInference = new float[arrayListShorts.size()];
        for (int i = 0; i < arrayListShorts.size(); i++) {
            floatsForInference[i] = arrayListShorts.get(i) / 32768F;
        }
        Log.d("Info_ESC50", "准备推理: " + String.valueOf(arrayListShorts.size()));
        Log.i("Info_ESC50", "pcm data demo: " + arrayListShorts.subList(Math.max(0, arrayListShorts.size() - 100), arrayListShorts.size()).toString());
        float[][] floatsForInference_Input = MelSpectrogramConverter.computeMelSpectrogram(floatsForInference);
        Log.d("Info_ESC50", "准备推理Mel: [" + String.valueOf(floatsForInference_Input.length) + "," + String.valueOf(floatsForInference_Input[0].length) + "]");
        Log.d("Info_ESC50", "Mel data demo: [" + Arrays.toString(floatsForInference_Input[0]) + "]");

        // Inference
        _inferenceDone.postValue(false);
        long startTime = System.nanoTime();
        for(int i=0;i<10;i++) {
            _listOfClasses.postValue(esc50_model_exe.execute(floatsForInference_Input));
        }
        long endTime = System.nanoTime();
        long elapsedTime = (endTime - startTime)/10;
//        _listOfClasses.postValue(esc50_model_exe.execute(floatsForInference_Input));
//        long endTime = System.nanoTime();
//        long elapsedTime = endTime - startTime;
        Log.i("Info_ESC50", TimeUnit.NANOSECONDS.toMillis(elapsedTime) + " 毫秒");
        _inferenceDone.postValue(true);
    }

    public void setUpdateLoopListeningHandler() {
        // Start loop for collecting sound and inferring
        updateLoopListeningHandler.postDelayed(updateLoopListeningRunnable, 0);
        Log.d("Info_ESC50", "Start: 开始获取Audio...");
    }

    public void stopAllListening() {
        // Remove queue of callbacks when user presses stop before the song stops
        updateLoopListeningHandler.removeCallbacks(updateLoopListeningRunnable);
        handler.removeCallbacksAndMessages(null);
        handlerStart.removeCallbacksAndMessages(null);
        _listeningEnd.postValue(true);
        Log.d("Info_ESC50", "Finish: 结束获取Audio!");
    }

    private final Runnable updateLoopListeningRunnable = new Runnable() {
        @Override
        public void run() {
            // Start listening
            startListening();
            _listeningEnd.postValue(false);

            // Stop after 5s
            handler.postDelayed(() -> stopListening(), ListeningFragment.UPDATE_INTERVAL_INFERENCE);

            // Re-run it after the update interval
            updateLoopListeningHandler.postDelayed(updateLoopListeningRunnable, ListeningFragment.UPDATE_INTERVAL_INFERENCE);
        }
    };

    @Override
    protected void onCleared() {
        super.onCleared();
        // Stop all listening when back button is used
        stopAllListening();
        esc50_model_exe.close();
        Log.d("Info_ESC50", "Finish: 结束获取Audio并释放资源!");
    }
}
