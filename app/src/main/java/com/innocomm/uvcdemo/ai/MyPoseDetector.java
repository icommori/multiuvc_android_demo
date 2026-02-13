package com.innocomm.uvcdemo.ai;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

public class MyPoseDetector implements AIDetector {
    private PoseDetector detector;

    @Override
    public void init() {
        PoseDetectorOptions options = new PoseDetectorOptions.Builder()
                .build();
        detector = PoseDetection.getClient(options);
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
        return "Pose Detector";
    }
}
