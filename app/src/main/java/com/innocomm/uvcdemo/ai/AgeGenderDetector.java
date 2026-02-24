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
                .setMinFaceSize(0.15f)
                .build();
        faceDetector = FaceDetection.getClient(faceOptions);

        try {
            int availableProcessors = Runtime.getRuntime().availableProcessors();
            int numThreads = (availableProcessors <= 2) ? 2 : 4;
            Log.v(TAG, "availableProcessors " + availableProcessors + ", numThreads " + numThreads);

            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(numThreads);

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

    private int nextPseudoId = 0;
    private final Map<Integer, Long> lastSeenMap = new HashMap<>();

    private List<AgeGenderResult> processFaces(List<Face> faces, Bitmap fullBitmap) {
        if (DEBUG_LOG) Log.v(TAG, "Processing " + faces.size() + " faces");
        List<AgeGenderResult> currentResults = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (Face face : faces) {
            Rect rect = face.getBoundingBox();
            int centerX = rect.centerX();
            int centerY = rect.centerY();
            
            // 找出最接近的舊人臉 (手動追蹤)
            int bestMatchId = -1;
            double minDistance = Double.MAX_VALUE;
            double threshold = (rect.width() + rect.height()) / 2.0 * 0.8; // 容許移動範圍

            for (Map.Entry<Integer, AgeGenderResult> entry : resultCache.entrySet()) {
                Rect cachedRect = entry.getValue().boundingBox;
                int dx = centerX - cachedRect.centerX();
                int dy = centerY - cachedRect.centerY();
                double dist = Math.sqrt(dx * dx + dy * dy);
                
                if (dist < threshold && dist < minDistance) {
                    minDistance = dist;
                    bestMatchId = entry.getKey();
                }
            }

            AgeGenderResult matched = (bestMatchId != -1) ? resultCache.get(bestMatchId) : null;
            boolean needsInference = (matched == null) || (now - matched.timestamp >= 10000);

            if (needsInference) {
                Bitmap cropped = cropBitmap(fullBitmap, rect);
                if (cropped != null) {
                    try {
                        float ageRaw = predictAge(cropped);
                        float[] genderProbs = predictGender(cropped);
                        int age = (int) (ageRaw * 116);
                        AgeGenderResult.Gender gender = (genderProbs[0] > genderProbs[1]) ? AgeGenderResult.Gender.MALE : AgeGenderResult.Gender.FEMALE;
                        
                        int targetId = (bestMatchId != -1) ? bestMatchId : nextPseudoId++;
                        AgeGenderResult result = new AgeGenderResult(targetId, rect, age, gender, now);
                        
                        currentResults.add(result);
                        resultCache.put(targetId, result);
                        lastSeenMap.put(targetId, now);
                        
                        if (DEBUG_LOG) Log.d(TAG, "New inference for ID " + targetId + ": " + age + " " + gender);
                    } catch (Exception e) {
                        Log.e(TAG, "Inference error", e);
                    } finally {
                        cropped.recycle();
                    }
                }
            } else {
                // 套用緩存結果，但更新座標
                AgeGenderResult updated = new AgeGenderResult(bestMatchId, rect, matched.age, matched.gender, matched.timestamp);
                currentResults.add(updated);
                resultCache.put(bestMatchId, updated);
                lastSeenMap.put(bestMatchId, now);
            }
        }

        // 清理超過 1 秒沒出現的人臉緩存
        resultCache.keySet().removeIf(id -> (now - lastSeenMap.getOrDefault(id, 0L)) > 1000);
        lastSeenMap.keySet().removeIf(id -> !resultCache.containsKey(id));

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
