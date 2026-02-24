package com.innocomm.uvcdemo.ai;

import android.graphics.Rect;

public class AgeGenderResult {
    public final int trackingId;
    public final Rect boundingBox;
    public final int age;
    public final Gender gender;
    public final long timestamp;

    public AgeGenderResult(int trackingId, Rect boundingBox, int age, Gender gender) {
        this(trackingId, boundingBox, age, gender, System.currentTimeMillis());
    }

    public AgeGenderResult(int trackingId, Rect boundingBox, int age, Gender gender, long timestamp) {
        this.trackingId = trackingId;
        this.boundingBox = boundingBox;
        this.age = age;
        this.gender = gender;
        this.timestamp = timestamp;
    }

    public enum Gender {
        MALE, FEMALE, UNKNOWN
    }
}
