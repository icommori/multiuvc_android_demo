package com.innocomm.uvcdemo.ai;

import android.graphics.Bitmap;

public interface AIDetector {
    void init();
    void detect(Bitmap bitmap, DetectCallback callback);
    void release();
    String getName();

    interface DetectCallback {
        void onResult(Object result);
    }
}
