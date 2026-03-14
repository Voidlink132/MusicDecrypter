package com.ncm2flac;

import java.io.File;

public interface OnConvertListener {
    // 转换开始回调，隐藏按钮，显示进度条
    void onStart();
    // 转换进度回调，progress 0-100
    void onProgress(int progress);
    // 转换成功回调
    void onSuccess(File outputFile);
    // 转换失败回调
    void onFail(String errorMsg);
}
