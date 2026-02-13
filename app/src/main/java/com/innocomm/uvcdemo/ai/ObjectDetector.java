package com.innocomm.uvcdemo.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.IOException;

public class ObjectDetector implements AIDetector {
    private static final String TAG = "ObjectDetector";
    private final Context context;
    private org.tensorflow.lite.task.vision.detector.ObjectDetector detector;

    public ObjectDetector(Context context) {
        this.context = context;
    }

    @Override
    public void init() {
        try {
            BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(4);
            if (new CompatibilityList().isDelegateSupportedOnThisDevice()) {
                baseOptionsBuilder.useGpu();
            }

            ObjectDetectorOptions options = ObjectDetectorOptions.builder()
                    .setScoreThreshold(0.5f)
                    .setMaxResults(10)
                    .setBaseOptions(baseOptionsBuilder.build())
                    .build();

            detector = org.tensorflow.lite.task.vision.detector.ObjectDetector.createFromFileAndOptions(
                    context, "efficientdet-lite0.tflite", options);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load model", e);
        }
    }

    @Override
    public void detect(Bitmap bitmap, DetectCallback callback) {
        if (detector == null) {
            callback.onResult(null);
            return;
        }
        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
        callback.onResult(detector.detect(tensorImage));
    }

    @Override
    public void release() {
        if (detector != null) {
            detector.close();
            detector = null;
        }
    }

    @Override
    public String getName() {
        return "Object Detector";
    }
}
