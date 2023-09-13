package com.wwh.esc50_recognition;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ListeningRecorder {
    private String mHotwordKey;
    private int numberRecordings;
    private Context mContext;

    private final int AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_RECOGNITION;
    private final int SAMPLE_RATE = 32000; //采样率
    private final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;  //单声道
    private final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;  //编码位数
    private final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING);
    private final AudioFormat AUDIO_FORMAT = new AudioFormat.Builder()
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_MASK)
            .build();
    private ByteArrayOutputStream mPcmStream;
    private AudioRecord mRecorder;
    private boolean mRecording;
    private Thread mThread;
    private double[] mSampleLengths;
    private int mSamplesTaken;
    private ArrayList<Short> bufferForInference;

//    private final int mMinimumVoice = 100;
//    private final int mMaximumSilence = 700;
//    private final int mUpperLimit = 100;
//    public static final int FRAME_SIZE = 80;

    private short[] buffer;

    //构造函数：类的构造函数接受一个热词（mHotwordKey）、一次统计记录次数（numberRecordings）和上下文对象（context）作为参数。
    // 构造函数初始化了一些成员变量，例如音频参数，录音缓冲区，和其他控制标志。
    public ListeningRecorder(String hotwordKey, int numberRecordings, Context context) {
        this.mHotwordKey = hotwordKey;
        this.numberRecordings = numberRecordings;
        this.mContext = context;

        this.mPcmStream = new ByteArrayOutputStream();
        this.bufferForInference = new ArrayList<>();
        // 记录是否在录音
        this.mRecording = false;
        this.mSampleLengths = new double[numberRecordings];
        this.mSamplesTaken = 0;
    }

    //用于启动录音过程。它使用 AudioRecord 初始化并启动音频录制。录制的音频数据被读取并经过处理
    // 然后写入 bufferForInference 和 mPcmStream 中
    private static final int RECORD_AUDIO_PERMISSION_CODE = 1; // 自定义请求代码
    public void startRecording() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((MainActivity)mContext, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
        mRecorder = new AudioRecord.Builder()
                .setAudioSource(AUDIO_SOURCE)
                .setAudioFormat(AUDIO_FORMAT)
                .setBufferSizeInBytes(BUFFER_SIZE)
                .build();
        bufferForInference = new ArrayList<>();
        // 开始录音
        mRecording = true;
        mRecorder.startRecording();
        // 开启另外一个线程读取audio
        mThread = new Thread(readAudio);
        mThread.start();
    }

    // value保存到output中
    private void writeShort(ByteArrayOutputStream output, short value) {
        output.write(value);
        output.write(value >> 8);
    }

    // 保存Audio的线程，这是一个用于在单独线程中读取音频数据的任务。
    // 它不断地从音频录制器中读取音频数据，将其存储在 bufferForInference 和 mPcmStream 中，并在必要时调整音量。
    private final Runnable readAudio = new Runnable() {
        @Override
        public void run() {
            int readBytes;
            Log.d("Info_ESC50","buffer_size: "+ BUFFER_SIZE);
            buffer = new short[BUFFER_SIZE];
            Log.d("Info_ESC50","开始录音时间: "+ TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()));
            while (mRecording) {
                readBytes = mRecorder.read(buffer, 0, BUFFER_SIZE);
                if (readBytes > 0) {
                    for (int i = 0; i < readBytes; i++) {
                        // 乘以6.7的操作涉及到音频信号的放大，最后还要取整。
                        // 这种操作通常用于增强音频信号的音量，以确保录制的音频具有适当的音频水平
                        buffer[i] = (short) Math.min((buffer[i] * 6.7), Short.MAX_VALUE);
                    }
                }
                if (readBytes != AudioRecord.ERROR_INVALID_OPERATION) {
                    for (short s : buffer) {
                        bufferForInference.add(s);
                        writeShort(mPcmStream, s);
                    }
                }
            }
            Log.d("Info_ESC50","结束录音时间"+ TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()));
            Log.d("Info_ESC50","bufferForInference"+ bufferForInference.size());
        }
    };

    // 录音过程。它停止 AudioRecord 的录音，释放资源，并返回录制的音频数据，保存在 mPcmStream 中
    public ByteArrayOutputStream stopRecording() {
        if (mRecorder != null && mRecorder.getState() == AudioRecord.STATE_INITIALIZED) {
            mRecording = false;
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
            Log.d("Info_ESC50","结束录音");
        }
        return mPcmStream;
    }

    // 这个方法返回录制的音频数据的一个副本，保存在 bufferForInference 中，以便进一步的处理或分析。
    public ArrayList<Short> stopRecordingForInference() {
        return bufferForInference;
    }

    // 这个方法将原始的 PCM 音频数据转换为 WAV 格式，并返回包含 WAV 数据的字节数组。
    // https://stackoverflow.com/questions/43569304/android-how-can-i-write-byte-to-wav-file
    private byte[] pcmToWav(ByteArrayOutputStream byteArrayOutputStream) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        byte[] pcmAudio = byteArrayOutputStream.toByteArray();
        writeString(stream, "RIFF");
        writeInt(stream, 36 + pcmAudio.length);
        writeString(stream, "WAVE");
        writeString(stream, "fmt ");
        writeInt(stream, 16);
        writeShort(stream, (short) 1);
        writeShort(stream, (short) 1);
        writeInt(stream, SAMPLE_RATE);
        writeInt(stream, SAMPLE_RATE * 2);
        writeShort(stream, (short) 2);
        writeShort(stream, (short) 16);
        writeString(stream, "data");
        writeInt(stream, pcmAudio.length);
        stream.write(pcmAudio);
        Log.d("Info_ESC50","PCM to WAV转化");
        return stream.toByteArray();
    }


    public void writeWav(ByteArrayOutputStream byteArrayOutputStream) {
        byte[] wav = new byte[0];
        try {
            wav = pcmToWav(byteArrayOutputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileOutputStream stream = null;
        try {
            stream = new FileOutputStream(
                    mContext.getFilesDir().toString() +
                            "/wwh.wav", false);
            Log.d("Info_ESC50","写入WAV到"+mContext.getFilesDir().toString()+"wwh.wav成功");
            stream.write(wav);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e) {
            Log.d("Info_ESC50","写入WAV到"+mContext.getFilesDir().toString()+"wwh.wav失败");
            Log.e("PERMIS_STORAGE_DENIED", "NOT ABLE TO WRITE .WAV TO SDCARD");
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //Write a 32-bit integer to an output stream, in Little Endian format小端写入32位
    private void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value);
        output.write(value >> 8);
        output.write(value >> 16);
        output.write(value >> 24);
    }

    //Write a string to an output stream.
    private void writeString(ByteArrayOutputStream output, String value) {
        for (int i = 0; i < value.length(); i++) {
            output.write(value.charAt(i));
        }
    }

    private JSONObject generateConfig() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("hotword_key", mHotwordKey);
            obj.put("kind", "personal");
            obj.put("dtw_ref", 0.22);
            obj.put("from_mfcc", 1);
            obj.put("to_mfcc", 13);
            obj.put("band_radius", 10);
            obj.put("shift", 10);
            obj.put("window_size", 10);
            obj.put("sample_rate", SAMPLE_RATE);
            obj.put("frame_length_ms", 25.0);
            obj.put("frame_shift_ms", 10.0);
            obj.put("num_mfcc", 13);
            obj.put("num_mel_bins", 13);
            obj.put("mel_low_freq", 20);
            obj.put("cepstral_lifter", 22.0);
            obj.put("dither", 0.0);
            obj.put("window_type", "povey");
            obj.put("use_energy", false);
            obj.put("energy_floor", 0.0);
            obj.put("raw_energy", true);
            obj.put("preemphasis_coefficient", 0.97);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return obj;
    }

    public void reInitializePcmStream() {
        mPcmStream = new ByteArrayOutputStream();
        bufferForInference = new ArrayList<>();
    }

}
