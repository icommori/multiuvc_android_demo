package com.innocomm.uvcdemo.ai;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.facemesh.FaceMeshDetection;
import com.google.mlkit.vision.facemesh.FaceMeshDetector;
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions;

public class MyFaceMeshDetector implements AIDetector {
    private FaceMeshDetector detector;

    @Override
    public void init() {
        detector = FaceMeshDetection.getClient(
                new FaceMeshDetectorOptions.Builder()
                        .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                        .build()
        );
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
        return "FaceMesh Detector";
    }
}
