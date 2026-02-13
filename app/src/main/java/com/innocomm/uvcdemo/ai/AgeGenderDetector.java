package com.innocomm.uvcdemo.ai;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgeGenderDetector implements AIDetector {
    private static final String TAG = "AgeGenderDetector";
    private final Context context;
    private com.google.mlkit.vision.face.FaceDetector faceDetector;
    private Interpreter ageInterpreter;
    private Interpreter genderInterpreter;

    // Based on error logs:
    // Age model (input_1) expects 480000 bytes -> 200x200 float32
    // Gender model (input_3) expects 196608 bytes -> 128x128 float32
    private final ImageProcessor ageImageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(200, 200, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))
            .build();

    private final ImageProcessor genderImageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(128, 128, ResizeOp.ResizeMethod.BILINEAR))
            .add(new NormalizeOp(127.5f, 127.5f))
            .build();

    private final Map<Integer, AgeGenderResult> resultCache = new HashMap<>();

    public AgeGenderDetector(Context context) {
        this.context = context;
    }

    @Override
    public void init() {
        FaceDetectorOptions faceOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .enableTracking()
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        try {
            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(4);

            ByteBuffer ageModel = FileUtil.loadMappedFile(context, "model_age_nonq.tflite");
            ByteBuffer genderModel = FileUtil.loadMappedFile(context, "model_gender_nonq.tflite");

            ageInterpreter = new Interpreter(ageModel, tfliteOptions);
            genderInterpreter = new Interpreter(genderModel, tfliteOptions);
            Log.d(TAG, "Successfully loaded Age and Gender TFLite models");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load TFLite models: " + e.getMessage(), e);
        }
    }

    @Override
    public void detect(Bitmap bitmap, DetectCallback callback) {
        if (faceDetector == null || ageInterpreter == null || genderInterpreter == null) {
            callback.onResult(null);
            return;
        }

        InputImage mlImage = InputImage.fromBitmap(bitmap, 0);
        faceDetector.process(mlImage)
                .addOnSuccessListener(faces -> {
                    try {
                        // Check if interpreters are still valid (not closed)
                        if (ageInterpreter == null || genderInterpreter == null) {
                            callback.onResult(null);
                            return;
                        }
                        List<AgeGenderResult> results = processFaces(faces, bitmap);
                        callback.onResult(results);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in Age/Gender post-processing", e);
                        callback.onResult(null);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Face detection failed", e);
                    callback.onResult(null);
                });
    }

    private List<AgeGenderResult> processFaces(List<Face> faces, Bitmap fullBitmap) {
        if (DEBUG_LOG) Log.v(TAG, "Processing " + faces.size() + " faces");
        List<AgeGenderResult> currentResults = new ArrayList<>();
        List<Integer> currentIds = new ArrayList<>();

        for (Face face : faces) {
            int id = face.getTrackingId() != null ? face.getTrackingId() : -1;
            Rect rect = face.getBoundingBox();
            currentIds.add(id);

            if (id != -1 && resultCache.containsKey(id)) {
                AgeGenderResult cached = resultCache.get(id);
                currentResults.add(new AgeGenderResult(id, rect, cached.age, cached.gender));
            } else {
                Bitmap cropped = cropBitmap(fullBitmap, rect);
                if (cropped != null) {
                    try {
                        float ageRaw = predictAge(cropped);
                        float[] genderProbs = predictGender(cropped);
                        
                        int age = (int) (ageRaw * 116);
                        AgeGenderResult.Gender gender = (genderProbs[0] > genderProbs[1]) ? AgeGenderResult.Gender.MALE : AgeGenderResult.Gender.FEMALE;
                        
                        Log.d(TAG, "ID: " + id + " -> RawAge: " + ageRaw + " (Age: " + age + "), GenderProbs: [" + genderProbs[0] + ", " + genderProbs[1] + "]");

                        AgeGenderResult result = new AgeGenderResult(id, rect, age, gender);
                        currentResults.add(result);
                        if (id != -1) {
                             resultCache.put(id, result);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Inference error for face", e);
                    } finally {
                        cropped.recycle();
                    }
                } else {
                    Log.w(TAG, "Failed to crop face for ID: " + id);
                }
            }
        }

        // Clean cache
        resultCache.keySet().removeIf(id -> !currentIds.contains(id));

        return currentResults;
    }

    private static final boolean DEBUG_LOG = false;

    private float predictAge(Bitmap cropped) {
        TensorImage input = TensorImage.fromBitmap(cropped);
        ByteBuffer buffer = ageImageProcessor.process(input).getBuffer();
        float[][] output = new float[1][1];
        ageInterpreter.run(buffer, output);
        return output[0][0];
    }

    private float[] predictGender(Bitmap cropped) {
        TensorImage input = TensorImage.fromBitmap(cropped);
        ByteBuffer buffer = genderImageProcessor.process(input).getBuffer();
        float[][] output = new float[1][2];
        genderInterpreter.run(buffer, output);
        return output[0];
    }

    private Bitmap cropBitmap(Bitmap bitmap, Rect rect) {
        int left = Math.max(0, rect.left);
        int top = Math.max(0, rect.top);
        int width = Math.min(rect.width(), bitmap.getWidth() - left);
        int height = Math.min(rect.height(), bitmap.getHeight() - top);
        if (width > 0 && height > 0) {
            return Bitmap.createBitmap(bitmap, left, top, width, height);
        }
        return null;
    }

    @Override
    public void release() {
        if (faceDetector != null) faceDetector.close();
        if (ageInterpreter != null) ageInterpreter.close();
        if (genderInterpreter != null) genderInterpreter.close();
        faceDetector = null;
        ageInterpreter = null;
        genderInterpreter = null;
    }

    @Override
    public String getName() {
        return "Age/Gender Detector";
    }
}
