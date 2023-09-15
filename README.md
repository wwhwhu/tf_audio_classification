# tf_audio_classification
# Java语言实现基于tenflowlite语音识别模型的音频识别APP

基于tensorflow 2.7.0导出的tflite进行音频事件识别, tflite使用ESC-50数据集

## APP介绍

实时对环境音量进行音频事件分类

## tflite模型

APP中包含tenflow-lite的移动端模型（CNN/CRNN），包含压缩以及不同的量化与剪枝模型版本

## 模型评估

对压缩前后不同模型在测试集上的效果以及CPU/内存/功耗进行了评估

## 实现

将tflite模型部署到手机上测试压缩前后的性能收益
