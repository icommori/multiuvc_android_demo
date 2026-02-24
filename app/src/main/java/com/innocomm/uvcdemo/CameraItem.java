/*
 * Copyright (C) 2026 InnoComm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Author: Mori Lin
 */

package com.innocomm.uvcdemo;

import android.hardware.usb.UsbDevice;

import com.herohan.uvcapp.ICameraHelper;

public class CameraItem {
    private UsbDevice device;
    private ICameraHelper cameraHelper;
    private String displayName;
    private boolean isConnected;
    private boolean isFailed;
    private boolean isPaused;
    
    public CameraItem(UsbDevice device, String displayName) {
        this.device = device;
        this.displayName = displayName;
        this.isConnected = false;
        this.isFailed = false;
        this.isPaused = false;
    }
    
    // ... (getters/setters for other fields)

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        isPaused = paused;
    }
    
    public UsbDevice getDevice() {
        return device;
    }
    
    public void setDevice(UsbDevice device) {
        this.device = device;
    }
    
    public ICameraHelper getCameraHelper() {
        return cameraHelper;
    }
    
    public void setCameraHelper(ICameraHelper cameraHelper) {
        this.cameraHelper = cameraHelper;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    
    public boolean isConnected() {
        return isConnected;
    }
    
    public void setConnected(boolean connected) {
        isConnected = connected;
    }
    
    public boolean isFailed() {
        return isFailed;
    }
    
    public void setFailed(boolean failed) {
        isFailed = failed;
    }
    
    public String getDeviceKey() {
        if (device == null) return "";
        return device.getVendorId() + "_" + device.getProductId() + "_" + device.getDeviceName();
    }

    private com.innocomm.uvcdemo.ai.AIManager.AIType currentAIType = com.innocomm.uvcdemo.ai.AIManager.AIType.NONE;
    private com.innocomm.uvcdemo.ai.AIDetector currentDetector;

    public com.innocomm.uvcdemo.ai.AIManager.AIType getCurrentAIType() {
        return currentAIType;
    }

    public void setCurrentAIType(com.innocomm.uvcdemo.ai.AIManager.AIType type) {
        this.currentAIType = type;
    }

    public com.innocomm.uvcdemo.ai.AIDetector getCurrentDetector() {
        return currentDetector;
    }

    public void setCurrentDetector(com.innocomm.uvcdemo.ai.AIDetector detector) {
        this.currentDetector = detector;
    }
    
    private com.innocomm.uvcdemo.ai.AIManager.AIType pendingAIType = com.innocomm.uvcdemo.ai.AIManager.AIType.NONE;

    public void setPendingAIType(com.innocomm.uvcdemo.ai.AIManager.AIType type) {
        this.pendingAIType = type;
    }

    public com.innocomm.uvcdemo.ai.AIManager.AIType getPendingAIType() {
        return pendingAIType;
    }

    private boolean isProjecting = false;
    private HdmiPresentation displayPresentation = null;

    public boolean isProjecting() { return isProjecting; }
    public void setProjecting(boolean projecting) { isProjecting = projecting; }
    
    public HdmiPresentation getDisplayPresentation() { return displayPresentation; }
    public void setDisplayPresentation(HdmiPresentation p) { displayPresentation = p; }
}

