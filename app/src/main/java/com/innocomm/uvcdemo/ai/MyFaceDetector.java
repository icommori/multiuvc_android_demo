package com.innocomm.uvcdemo.ai;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class MyFaceDetector implements AIDetector {
    private FaceDetector detector;

    @Override
    public void init() {
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();
        detector = FaceDetection.getClient(options);
    }

    @Override
    public void detect(Bitmap bitmap, DetectCallback callback) {
        if (detector == null) {
            callback.onResult(null);
            return;
        }
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        detector.process(image)
                .addOnSuccessListener(callback::onResult)
                .addOnFailureListener(e -> callback.onResult(null));
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
        return "Face Detector";
    }
}
