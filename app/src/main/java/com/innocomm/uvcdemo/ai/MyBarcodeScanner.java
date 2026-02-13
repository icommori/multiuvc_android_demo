package com.innocomm.uvcdemo.ai;

import android.graphics.Bitmap;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

public class MyBarcodeScanner implements AIDetector {
    private BarcodeScanner detector;

    @Override
    public void init() {
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build();
        detector = BarcodeScanning.getClient(options);
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
        return "Barcode Scanner";
    }
}
