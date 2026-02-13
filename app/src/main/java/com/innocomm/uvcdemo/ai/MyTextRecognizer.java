package com.innocomm.uvcdemo.ai;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

public class MyTextRecognizer implements AIDetector {
    private TextRecognizer detector;

    @Override
    public void init() {
        detector = TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
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
        return "Text Recognizer";
    }
}
