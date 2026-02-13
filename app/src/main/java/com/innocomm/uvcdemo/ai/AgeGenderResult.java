package com.innocomm.uvcdemo.ai;

import android.graphics.Rect;

public class AgeGenderResult {
    public final int trackingId;
    public final Rect boundingBox;
    public final int age;
    public final Gender gender;

    public AgeGenderResult(int trackingId, Rect boundingBox, int age, Gender gender) {
        this.trackingId = trackingId;
        this.boundingBox = boundingBox;
        this.age = age;
        this.gender = gender;
    }

    public enum Gender {
        MALE, FEMALE, UNKNOWN
    }
}
