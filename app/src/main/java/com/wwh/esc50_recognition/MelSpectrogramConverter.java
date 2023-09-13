package com.wwh.esc50_recognition;

import android.util.Log;

import org.jtransforms.fft.FloatFFT_1D;

public class MelSpectrogramConverter {

    // Constants
    private static final int N_FFT = 1024;  // FFT大小
    private static final int HOP_LENGTH = 128; // 帧移大小
    private static final int N_MELS = 128; // Mel滤波器数量
    private static final int SR = 32000; // 采样率

    public static float[][] computeMelSpectrogram(float[] audioData) {
        int audioLength = audioData.length;
        // nFrames 1251
        int nFrames = (int) Math.floor(audioLength / HOP_LENGTH) + 1;

        // 初始化一个数组来保存STFT结果
        Log.d("Info_ESC50", "STFT shape: [" + Integer.toString(N_FFT / 2 + 1) + Integer.toString(nFrames) + "]");
        float[][] stft = new float[N_FFT / 2 + 1][nFrames];

        // 初始化STFT计算器
        FloatFFT_1D fft = new FloatFFT_1D(N_FFT);

        // 计算STFT
        for (int t = 0; t < nFrames; t++) {
            // frame最大1024
            float[] frame = new float[N_FFT];
            // 填充音频数据到帧中
            for (int i = 0; i < N_FFT && t * HOP_LENGTH + i < audioLength; i++) {
                frame[i] = audioData[t * HOP_LENGTH + i];
            }
            // 执行FFT
            fft.realForward(frame);
            // 计算功率谱
            for (int f = 0; f < N_FFT / 2; f++) {
                // f最大511
                stft[f][t] = frame[2 * f] * frame[2 * f] + frame[2 * f + 1] * frame[2 * f + 1];
            }
        }

        // 初始化一个数组来保存梅尔频谱
        float[][] melSpectrogram = new float[N_MELS][nFrames];

        // 计算梅尔滤波器
        double[] melFilter = createMelFilter();

        // 应用梅尔滤波器到功率谱
        for (int t = 0; t < nFrames; t++) {
            for (int m = 0; m < N_MELS; m++) {
                float sum = 0.0f;
                for (int f = 0; f < N_FFT / 2 + 1; f++) {
                    sum += stft[f][t] * melFilter[m * (N_FFT / 2 + 1) + f];
                }
                melSpectrogram[m][t] = sum;
            }
        }

        return melSpectrogram;
    }

    private static double[] createMelFilter() {
        // 创建Mel滤波器
        double[] melFilter = new double[N_MELS * (N_FFT / 2 + 1)];

        // 计算Mel频率
        double[] melFrequencies = new double[N_MELS + 2];
        for (int m = 0; m < N_MELS + 2; m++) {
            melFrequencies[m] = 700.0 * (Math.pow(10.0, m / 2595.0) - 1.0);
        }

        // 计算Mel滤波器的频率响应
        for (int m = 0; m < N_MELS; m++) {
            for (int f = 0; f < N_FFT / 2 + 1; f++) {
                double centerF = melFrequencies[m + 1];
                double fPrev = melFrequencies[m];
                double fNext = melFrequencies[m + 2];
                if (f >= fPrev && f <= centerF) {
                    melFilter[m * (N_FFT / 2 + 1) + f] = (f - fPrev) / (centerF - fPrev);
                } else if (f >= centerF && f <= fNext) {
                    melFilter[m * (N_FFT / 2 + 1) + f] = (fNext - f) / (fNext - centerF);
                } else {
                    melFilter[m * (N_FFT / 2 + 1) + f] = 0.0;
                }
            }
        }

        return melFilter;
    }
}
