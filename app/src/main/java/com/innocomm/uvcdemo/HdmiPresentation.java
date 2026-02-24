package com.innocomm.uvcdemo;

import android.app.Presentation;
import android.content.Context;
import android.os.Bundle;
import android.view.Display;

import com.serenegiant.widget.AspectRatioSurfaceView;

public class HdmiPresentation extends Presentation {

    private AspectRatioSurfaceView svCameraView;
    private OverlayView overlayView;

    public HdmiPresentation(Context outerContext, Display display) {
        super(outerContext, display);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.presentation_camera);

        svCameraView = findViewById(R.id.svCameraView);
        overlayView = findViewById(R.id.overlayView);
    }

    public AspectRatioSurfaceView getCameraView() {
        return svCameraView;
    }

    public OverlayView getOverlayView() {
        return overlayView;
    }
}
