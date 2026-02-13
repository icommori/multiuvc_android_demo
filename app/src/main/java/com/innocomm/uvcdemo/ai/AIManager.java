package com.innocomm.uvcdemo.ai;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class AIManager {
    public enum AIType {
        NONE("None"),
        OBJECT_DETECT("Object Detect"),
        FACE_DETECT("Face Detect"),
        FACEMESH_DETECT("FaceMesh Detect"),
        POSE_DETECT("Pose Detect"),
        QRCODE_DETECT("QRCode Detect"),
        TEXT_RECOGNITION("Text Recognition(中)"),
        AGE_GENDER_DETECT("Age / Gender Detect");

        private final String displayName;
        AIType(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }

    private static AIManager instance;
    private final Map<AIType, AIDetector> detectors = new HashMap<>();
    private final Map<AIType, String> activeUsers = new HashMap<>(); // AIType -> CameraID/DisplayName
    private Context context;

    private AIManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized AIManager getInstance(Context context) {
        if (instance == null) {
            instance = new AIManager(context);
        }
        return instance;
    }

    public synchronized AIDetector getDetector(AIType type, String userId) {
        if (type == AIType.NONE) return null;

        // Check if already used by someone else
        if (activeUsers.containsKey(type) && !activeUsers.get(type).equals(userId)) {
            return null; // Busy
        }

        AIDetector detector = detectors.get(type);
        if (detector == null) {
            detector = createDetector(type);
            if (detector != null) {
                detector.init();
                detectors.put(type, detector);
            }
        }

        if (detector != null) {
            activeUsers.put(type, userId);
        }

        return detector;
    }

    public synchronized void releaseDetector(AIType type, String userId) {
        if (type == AIType.NONE) return;
        Log.v("innocomm","releaseDetector "+type);
        if (activeUsers.containsKey(type) && activeUsers.get(type).equals(userId)) {
            activeUsers.remove(type);
            // We can choose to keep the detector in memory (lazy release)
            // or release it immediately if no one is using it.
            // The user asked for "initialized when needed and released when not used".
            // Since we only allow ONE user per AI, removing from activeUsers IS releasing its usage.
            // For actual memory release:
            AIDetector detector = detectors.remove(type);
            if (detector != null) {
                detector.release();
            }
        }
    }

    public synchronized boolean isAIBusy(AIType type, String userId) {
        if (type == AIType.NONE) return false;
        return activeUsers.containsKey(type) && !activeUsers.get(type).equals(userId);
    }

    private AIDetector createDetector(AIType type) {
        switch (type) {
            case OBJECT_DETECT:
                return new ObjectDetector(context);
            case FACE_DETECT:
                return new MyFaceDetector();
            case FACEMESH_DETECT:
                return new MyFaceMeshDetector();
            case POSE_DETECT:
                return new MyPoseDetector();
            case QRCODE_DETECT:
                return new MyBarcodeScanner();
            case TEXT_RECOGNITION:
                return new MyTextRecognizer();
            case AGE_GENDER_DETECT:
                return new AgeGenderDetector(context);
            default:
                return null;
        }
    }
}
