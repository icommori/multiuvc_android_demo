package com.innocomm.uvcdemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.facemesh.FaceMeshPoint;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseLandmark;
import com.google.mlkit.vision.text.Text;
import com.innocomm.uvcdemo.ai.AgeGenderResult;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class OverlayView extends View {

    public enum Mode {
        NORMAL, OBJECT, FACE, POSE, FACEMESH, QRCODE, TEXT, AGE_GENDER
    }

    private Mode currentMode = Mode.NORMAL;

    private List<Detection> rawResults = new LinkedList<>();
    private List<Face> faceResults = new LinkedList<>();
    private Pose poseResults = null;
    private List<FaceMesh> meshResults = new LinkedList<>();
    private List<Barcode> barcodeResults = new LinkedList<>();
    private Text textResults = null;
    private List<AgeGenderResult> ageGenderResults = new LinkedList<>();

    private Paint boxPaint = new Paint();
    private Paint textBackgroundPaint = new Paint();
    private Paint textPaint = new Paint();
    private Paint linePaint = new Paint();
    private Paint dotPaint = new Paint();

    private float scaleFactor = 1.0f;
    private float offsetX = 0f;
    private float offsetY = 0f;
    private Rect bounds = new Rect();

    private boolean isMirrored = false;
    private boolean isCenterCrop = false;

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setTextSize(24f);

        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(24f);

        boxPaint.setColor(Color.GREEN);
        boxPaint.setStrokeWidth(6F);
        boxPaint.setStyle(Paint.Style.STROKE);

        linePaint.setColor(Color.CYAN);
        linePaint.setStrokeWidth(4F);
        linePaint.setStyle(Paint.Style.STROKE);

        dotPaint.setColor(Color.YELLOW);
        dotPaint.setStrokeWidth(2f);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    public void clear() {
        currentMode = Mode.NORMAL;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Debug box to verify overlay is visible
        boxPaint.setColor(Color.MAGENTA);
        boxPaint.setStrokeWidth(10f);
        //canvas.drawRect(20, 20, 150, 150, boxPaint);
        
        Log.v("OverlayView", "onDraw currentMode=" + currentMode);

        switch (currentMode) {
            case OBJECT:
                drawObjectDetections(canvas);
                break;
            case FACE:
                drawFaces(canvas);
                break;
            case POSE:
                drawPose(canvas);
                break;
            case FACEMESH:
                drawFaceMesh(canvas);
                break;
            case QRCODE:
                drawBarcodes(canvas);
                break;
            case TEXT:
                drawTextRecognition(canvas);
                break;
            case AGE_GENDER:
                drawAgeGender(canvas);
                break;
        }
    }

    private void drawObjectDetections(Canvas canvas) {
        for (Detection d : rawResults) {
            RectF rect = mapRect(d.getBoundingBox());
            String label = (!d.getCategories().isEmpty()) ? d.getCategories().get(0).getLabel() : "object";
            float score = (!d.getCategories().isEmpty()) ? d.getCategories().get(0).getScore() : 0f;
            int color = getObjectColor(label);

            boxPaint.setColor(color);
            canvas.drawRect(rect, boxPaint);

            String text = String.format("%s %.2f", label, score);
            textBackgroundPaint.setColor(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
            textBackgroundPaint.getTextBounds(text, 0, text.length(), bounds);

            canvas.drawRect(rect.left, rect.top, rect.left + bounds.width() + 8, rect.top + bounds.height() + 8, textBackgroundPaint);
            canvas.drawText(text, rect.left, rect.top + bounds.height(), textPaint);
        }
    }

    private void drawFaces(Canvas canvas) {
        for (Face face : faceResults) {
            RectF rect = mapRect(new RectF(face.getBoundingBox()));
            int color = Color.YELLOW;
            boxPaint.setColor(color);
            canvas.drawRect(rect, boxPaint);

            String text = "Face";
            textBackgroundPaint.setColor(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
            textBackgroundPaint.getTextBounds(text, 0, text.length(), bounds);

            canvas.drawRect(rect.left, rect.top - bounds.height() - 8, rect.left + bounds.width() + 8, rect.top, textBackgroundPaint);
            canvas.drawText(text, rect.left, rect.top - 8, textPaint);
        }
    }

    private void drawPose(Canvas canvas) {
        if (poseResults == null) return;
        List<PoseLandmark> landmarks = poseResults.getAllPoseLandmarks();
        if (landmarks.isEmpty()) return;

        for (PoseLandmark landmark : landmarks) {
            canvas.drawCircle(mapX(landmark.getPosition().x), mapY(landmark.getPosition().y), 6f, linePaint);
        }

        drawLine(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER);
        drawLine(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP);
        drawLine(canvas, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP);
        drawLine(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP);
        drawLine(canvas, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW);
        drawLine(canvas, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST);
        drawLine(canvas, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW);
        drawLine(canvas, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST);
        drawLine(canvas, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE);
        drawLine(canvas, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE);
        drawLine(canvas, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE);
        drawLine(canvas, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE);
    }

    private void drawLine(Canvas canvas, int startType, int endType) {
        PoseLandmark start = poseResults.getPoseLandmark(startType);
        PoseLandmark end = poseResults.getPoseLandmark(endType);
        if (start != null && end != null) {
            canvas.drawLine(mapX(start.getPosition().x), mapY(start.getPosition().y),
                    mapX(end.getPosition().x), mapY(end.getPosition().y), linePaint);
        }
    }

    private void drawFaceMesh(Canvas canvas) {
        for (FaceMesh mesh : meshResults) {
            for (FaceMeshPoint point : mesh.getAllPoints()) {
                canvas.drawCircle(mapX(point.getPosition().getX()), mapY(point.getPosition().getY()), 2f, dotPaint);
            }
            RectF bbox = mapRect(new RectF(mesh.getBoundingBox()));
            boxPaint.setColor(Color.MAGENTA);
            canvas.drawRect(bbox, boxPaint);
        }
    }

    private void drawBarcodes(Canvas canvas) {
        for (Barcode barcode : barcodeResults) {
            RectF rect = mapRect(new RectF(barcode.getBoundingBox()));
            int color = Color.RED;
            boxPaint.setColor(color);
            canvas.drawRect(rect, boxPaint);

            String text = barcode.getDisplayValue() != null ? barcode.getDisplayValue() : "QR Code";
            textBackgroundPaint.setColor(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
            textBackgroundPaint.getTextBounds(text, 0, text.length(), bounds);

            canvas.drawRect(rect.left, rect.top - bounds.height() - 8, rect.left + bounds.width() + 8, rect.top, textBackgroundPaint);
            canvas.drawText(text, rect.left, rect.top - 8, textPaint);
        }
    }

    private void drawTextRecognition(Canvas canvas) {
        if (textResults == null) return;
        for (Text.TextBlock block : textResults.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                RectF rect = mapRect(new RectF(line.getBoundingBox()));
                int color = Color.BLUE;
                boxPaint.setColor(color);
                canvas.drawRect(rect, boxPaint);

                String text = line.getText();
                textBackgroundPaint.setColor(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
                textBackgroundPaint.getTextBounds(text, 0, text.length(), bounds);

                canvas.drawRect(rect.left, rect.top - bounds.height() - 8, rect.left + bounds.width() + 8, rect.top, textBackgroundPaint);
                canvas.drawText(text, rect.left, rect.top - 8, textPaint);
            }
        }
    }

    private void drawAgeGender(Canvas canvas) {
        for (AgeGenderResult item : ageGenderResults) {
            RectF rect = mapRect(new RectF(item.boundingBox));
            int color = (item.gender == AgeGenderResult.Gender.FEMALE) ? Color.parseColor("#FFC0CB") :
                    (item.gender == AgeGenderResult.Gender.MALE) ? Color.BLUE : Color.GRAY;

            boxPaint.setColor(color);
            boxPaint.setStrokeWidth(8f);
            canvas.drawRect(rect, boxPaint);

            String text = String.format("%d / %s", item.age, 
                (item.gender == AgeGenderResult.Gender.MALE) ? "M" : 
                (item.gender == AgeGenderResult.Gender.FEMALE) ? "F" : "U");
                
            textBackgroundPaint.setColor(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
            textBackgroundPaint.getTextBounds(text, 0, text.length(), bounds);

            canvas.drawRect(rect.left, rect.top - bounds.height() - 12, rect.left + bounds.width() + 12, rect.top, textBackgroundPaint);
            canvas.drawText(text, rect.left + 6, rect.top - 10, textPaint);
            
            if (DEBUG_LOG) Log.v("OverlayView", "Drawing AgeGender label: " + text + " at " + rect.toString());
        }
    }
    
    private static final boolean DEBUG_LOG = true;

    private float mapX(float x) {
        float scaledX = x * scaleFactor + offsetX;
        if (isMirrored) scaledX = getWidth() - scaledX;
        return scaledX;
    }

    private float mapY(float y) {
        return y * scaleFactor + offsetY;
    }

    private RectF mapRect(RectF rect) {
        float left = mapX(rect.left);
        float right = mapX(rect.right);
        float top = mapY(rect.top);
        float bottom = mapY(rect.bottom);
        return new RectF(Math.min(left, right), Math.min(top, bottom), Math.max(left, right), Math.max(top, bottom));
    }

    private void updateScale(int imageHeight, int imageWidth) {
        float viewW = getWidth();
        float viewH = getHeight();
        Log.d("OverlayView", "updateScale view=" + viewW + "x" + viewH + " image=" + imageWidth + "x" + imageHeight);
        if (viewW == 0 || viewH == 0) return;
        scaleFactor = isCenterCrop ? Math.max(viewW / imageWidth, viewH / imageHeight) :
                Math.min(viewW / imageWidth, viewH / imageHeight);
        float scaledW = imageWidth * scaleFactor;
        float scaledH = imageHeight * scaleFactor;
        offsetX = (viewW - scaledW) / 2f;
        offsetY = (viewH - scaledH) / 2f;
        Log.d("OverlayView", "scaleFactor=" + scaleFactor + " offset=" + offsetX + "," + offsetY);
    }

    public void setResults(List<Detection> results, int h, int w) {
        currentMode = Mode.OBJECT;
        rawResults = results;
        updateScale(h, w);
        invalidate();
    }

    public void setFaceResults(List<Face> faces, int h, int w) {
        currentMode = Mode.FACE;
        faceResults = faces;
        updateScale(h, w);
        invalidate();
    }

    public void setPoseResults(Pose pose, int h, int w) {
        currentMode = Mode.POSE;
        poseResults = pose;
        updateScale(h, w);
        invalidate();
    }

    public void setMeshResults(List<FaceMesh> meshes, int h, int w) {
        currentMode = Mode.FACEMESH;
        meshResults = meshes;
        updateScale(h, w);
        invalidate();
    }

    public void setBarcodeResults(List<Barcode> barcodes, int h, int w) {
        currentMode = Mode.QRCODE;
        barcodeResults = barcodes;
        updateScale(h, w);
        invalidate();
    }

    public void setTextResults(Text text, int h, int w) {
        currentMode = Mode.TEXT;
        textResults = text;
        updateScale(h, w);
        invalidate();
    }

    public void setAgeGenderResults(List<AgeGenderResult> results, int h, int w) {
        currentMode = Mode.AGE_GENDER;
        ageGenderResults = results;
        updateScale(h, w);
        invalidate();
    }

    public void setMirrored(boolean mirrored) {
        isMirrored = mirrored;
    }

    private static final int[] COLORS = generateDistinctColors(100);
    private final Map<String, Integer> labeledColorMap = new HashMap<>();

    private int getObjectColor(String label) {
        if (!labeledColorMap.containsKey(label)) {
            labeledColorMap.put(label, COLORS[labeledColorMap.size() % COLORS.length]);
        }
        return labeledColorMap.get(label);
    }

    private static int[] generateDistinctColors(int count) {
        int[] colors = new int[count];
        float[] hues = {0f, 35f, 60f, 120f, 180f, 240f, 280f};
        for (int i = 0; i < count; i++) {
            float hue = (hues[i % hues.length] + (i / hues.length) * 10) % 360f;
            colors[i] = Color.HSVToColor(new float[]{hue, 1f, 1f});
        }
        return colors;
    }
}
