package com.wwh.esc50_recognition;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ModelExecutor {
    private int numberThreads = 2;
    private Interpreter interpreter;
    private long predictTime = 0L;
    private Map<Integer, String> labels;
    private final static int class_num = 50;
    private boolean isINT8 = false;

    // 模型文件名
    private static final String ESC50_MODEL = "CRNN_prune2_ex.tflite";

    public ModelExecutor(Context context, boolean useGPU) throws IOException {
        interpreter = getInterpreter(context, ESC50_MODEL, useGPU);
        Log.d("Info_ESC50", "已经获得模型");
        labels = new HashMap<>();
        labels.put(0, "dog");
        labels.put(14, "chirping_birds");
        labels.put(36, "vacuum_cleaner");
        labels.put(19, "thunderstorm");
        labels.put(30, "door_wood_knock");
        labels.put(34, "can_opening");
        labels.put(9, "crow");
        labels.put(22, "clapping");
        labels.put(48, "fireworks");
        labels.put(41, "chainsaw");
        labels.put(47, "airplane");
        labels.put(31, "mouse_click");
        labels.put(17, "pouring_water");
        labels.put(45, "train");
        labels.put(8, "sheep");
        labels.put(15, "water_drops");
        labels.put(46, "church_bells");
        labels.put(37, "clock_alarm");
        labels.put(32, "keyboard_typing");
        labels.put(16, "wind");
        labels.put(25, "footsteps");
        labels.put(4, "frog");
        labels.put(3, "cow");
        labels.put(27, "brushing_teeth");
        labels.put(43, "car_horn");
        labels.put(12, "crackling_fire");
        labels.put(40, "helicopter");
        labels.put(29, "drinking_sipping");
        labels.put(10, "rain");
        labels.put(7, "insects");
        labels.put(26, "laughing");
        labels.put(6, "hen");
        labels.put(44, "engine");
        labels.put(23, "breathing");
        labels.put(20, "crying_baby");
        labels.put(49, "hand_saw");
        labels.put(24, "coughing");
        labels.put(39, "glass_breaking");
        labels.put(28, "snoring");
        labels.put(18, "toilet_flush");
        labels.put(2, "pig");
        labels.put(35, "washing_machine");
        labels.put(38, "clock_tick");
        labels.put(21, "sneezing");
        labels.put(1, "rooster");
        labels.put(11, "sea_waves");
        labels.put(42, "siren");
        labels.put(5, "cat");
        labels.put(33, "door_wood_creaks");
        labels.put(13, "crickets");
    }

    public Pair<ArrayList<String>, ArrayList<Float>> execute(float[][] floatsInput) {
        predictTime = System.currentTimeMillis();
        float[][] inputValues = floatsInput;

        int numRows = inputValues.length;
        int numCols = inputValues[0].length;

        float[][][][] expandedArray = new float[1][numRows][numCols][1];  // 创建新的四维float数组
        byte[][][][] expandedArray2 = new byte[1][numRows][numCols][1];  // 创建新的四维float数组
        // 保存结果的数组
        float[][] arrayScores = new float[1][class_num];
        if(isINT8)
        {
            //do_something_int8_quantize_model
        }
        else {
            // 复制原始数组到新数组中
            for (int i = 0; i < numRows; i++) {
                for (int j = 0; j < numCols; j++) {
                    expandedArray[0][i][j][0] = inputValues[i][j];
                }
            }
            Log.d("Info_ESC50", "Model Input size：[" + Integer.toString(expandedArray.length)
                    + "," + Integer.toString(expandedArray[0].length)
                    + "," + Integer.toString(expandedArray[0][0].length)
                    + "," + Integer.toString(expandedArray[0][0][0].length) + "]");
            Object[] inputs = {expandedArray};
            HashMap<Integer, Object> outputs = new HashMap<>();
            outputs.put(0, arrayScores);

            try {
                interpreter.runForMultipleInputsOutputs(inputs, outputs);
            } catch (Exception e) {
                Log.e("EXCEPTION", e.toString());
            }
        }
        // 复制结果数组
        ArrayList<Float> listOfArrayMeanScores = new ArrayList<>();
        for (float score : arrayScores[0]) {
            listOfArrayMeanScores.add(score);
        }
        // 找到前十个最大的结果类的得分列表
        ArrayList<Float> listOfMaximumValues = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float max = 0;
            for (int j = 0; j < listOfArrayMeanScores.size(); j++) {
                if (listOfArrayMeanScores.get(j) > max) {
                    max = listOfArrayMeanScores.get(j);
                }
            }
            listOfMaximumValues.add(max);
            listOfArrayMeanScores.remove(listOfArrayMeanScores.indexOf(max));
        }

        // 确定这十个类的索引列表
        ArrayList<Integer> listOfMaxIndices = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (int k = 0; k < arrayScores[0].length; k++) {
                if (listOfMaximumValues.get(i).equals(arrayScores[0][k])) {
                    listOfMaxIndices.add(k);
                }
            }
        }

        Log.d("Info_ESC50", "Audio预测分类得分结果：" + java.util.Arrays.toString(arrayScores[0]));
        Log.d("Info_ESC50", "Audio预测分类得分size：" + String.valueOf(arrayScores[0].length));
        Log.d("Info_ESC50", "Audio预测分类得分索引排序：" + listOfMaxIndices.toString());

        //  确定这十个类的对应真实类的列表
        ArrayList<String> finalListOfOutputs = new ArrayList<>();
        for (int i : listOfMaxIndices) {
            finalListOfOutputs.add(labels.get(i));
        }

        return new Pair<>(finalListOfOutputs, listOfMaximumValues);
    }

    // 加载模型文件
    private MappedByteBuffer loadModelFile(Context context, String modelFile) throws IOException {
        Log.d("Info_ESC", Boolean.toString(context == null));
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFile);
        // File file = new File(context.getFilesDir(), modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        MappedByteBuffer retFile = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        inputStream.close();
        return retFile;
    }

    // 获取模型
    private Interpreter getInterpreter(Context context, String modelName, boolean useGpu) throws IOException {
        Interpreter.Options tfliteOptions = new Interpreter.Options();
        tfliteOptions.setNumThreads(numberThreads);
        return new Interpreter(loadModelFile(context, modelName), tfliteOptions);
    }

    // 释放模型
    public void close() {
        Log.d("Info_ESC50", "关闭模型");
        interpreter.close();
    }
}
