package com.innocomm.uvcdemo.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.ObjectDetector.ObjectDetectorOptions;

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
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int numThreads;
            if (availableProcessors <= 2) {
                numThreads = 2;
            } else if (availableProcessors <= 4) {
                numThreads = 4;
            } else {
                numThreads = 4; // Optimal performance/power ratio
            }
            
            Log.v(TAG, "availableProcessors " + availableProcessors + ", numThreads " + numThreads);

            BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder().setNumThreads(numThreads);
            
            // Use NNAPI as requested
            try {
                baseOptionsBuilder.useNnapi();
                Log.i(TAG, "Delegating to NNAPI");
            } catch (Exception e) {
                Log.w(TAG, "NNAPI not available, fallback to CPU: " + e.getMessage());
            }

            ObjectDetectorOptions options = ObjectDetectorOptions.builder()
                    .setScoreThreshold(0.5f)
                    .setMaxResults(50)
                    .setBaseOptions(baseOptionsBuilder.build())
                    .build();

            detector = org.tensorflow.lite.task.vision.detector.ObjectDetector.createFromFileAndOptions(
                    context, "efficientdet-lite0.tflite", options);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load model", e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "TFLite failed to load model with error: " + e.getMessage());
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
