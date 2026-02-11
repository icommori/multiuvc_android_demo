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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.content.BroadcastReceiver;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiCameraNewActivity extends AppCompatActivity implements CameraAdapter.OnCameraActionListener {

    private static final boolean DEBUG = true;
    private static final String TAG = MultiCameraNewActivity.class.getSimpleName();
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private RecyclerView rvCameraList;
    private CameraAdapter cameraAdapter;
    private List<CameraItem> cameraItems = new ArrayList<>();
    private Map<String, CameraItem> cameraItemMap = new HashMap<>();
    
    private HandlerThread mHandlerThread;
    private Handler mAsyncHandler;
    private Handler mMainHandler;

    private static final int REQUEST_PERMISSION_CODE = 123;
    private static final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_camera_new);
        setTitle(R.string.entry_multi_camera_new);

        mMainHandler = new Handler(getMainLooper());
        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mAsyncHandler = new Handler(mHandlerThread.getLooper());

        if (checkAndRequestPermissions()) {
            initViews();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.multi_camera_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            discoverAndInitCameras();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        for (String p : REQUIRED_PERMISSIONS) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, p) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(p);
            }
        }
        if (!listPermissionsNeeded.isEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), REQUEST_PERMISSION_CODE);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initViews();
                discoverAndInitCameras();
            } else {
                android.widget.Toast.makeText(this, "Permissions required", android.widget.Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseAllCameras();
        // monitoringHelper removed
        mHandlerThread.quitSafely();
        mAsyncHandler.removeCallbacksAndMessages(null);
    }

    private void initViews() {
        rvCameraList = findViewById(R.id.rvCameraList);
        
        // Determine span count based on orientation
        // Portrait: 2 columns, Landscape: 3 columns
        int orientation = getResources().getConfiguration().orientation;
        int spanCount = (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ? 3 : 2;
        
        androidx.recyclerview.widget.GridLayoutManager gridLayoutManager = 
            new androidx.recyclerview.widget.GridLayoutManager(this, spanCount);
        rvCameraList.setLayoutManager(gridLayoutManager);
        
        cameraAdapter = new CameraAdapter(this);
        rvCameraList.setAdapter(cameraAdapter);
        
        setupItemTouchHelper();
    }

    private void setupItemTouchHelper() {
        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                if (fromPosition < toPosition) {
                    for (int i = fromPosition; i < toPosition; i++) {
                        java.util.Collections.swap(cameraItems, i, i + 1);
                    }
                } else {
                    for (int i = fromPosition; i > toPosition; i--) {
                        java.util.Collections.swap(cameraItems, i, i - 1);
                    }
                }
                cameraAdapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Not supported
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(rvCameraList);
    }

    @Override
    protected void onStart() {
        if (DEBUG) Log.d(TAG, "onStart:");
        super.onStart();
        if (checkAndRequestPermissions()) {
            registerUsbReceiver();
            discoverAndInitCameras();
        }
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(TAG, "onStop:");
        super.onStop();
        unregisterUsbReceiver();
        releaseAllCameras();
    }

    private BroadcastReceiver mUsbReceiver;

    private void registerUsbReceiver() {
        if (mUsbReceiver == null) {
            mUsbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(android.content.Context context, android.content.Intent intent) {
                    String action = intent.getAction();
                    if (android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                        UsbDevice device = intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE);
                        if (device != null && isUvcCamera(device)) {
                            if (DEBUG) Log.d(TAG, "USB Device Attached: " + device.getDeviceName());
                            handleDeviceAttach(device);
                        }
                    } else if (android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                        UsbDevice device = intent.getParcelableExtra(android.hardware.usb.UsbManager.EXTRA_DEVICE);
                        if (device != null) {
                            if (DEBUG) Log.d(TAG, "USB Device Detached: " + device.getDeviceName());
                            handleDeviceDetach(device);
                        }
                    }
                }
            };
            android.content.IntentFilter filter = new android.content.IntentFilter();
            filter.addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(android.hardware.usb.UsbManager.ACTION_USB_DEVICE_DETACHED);
            registerReceiver(mUsbReceiver, filter);
        }
    }

    private void unregisterUsbReceiver() {
        if (mUsbReceiver != null) {
            unregisterReceiver(mUsbReceiver);
            mUsbReceiver = null;
        }
    }

    private boolean isUvcCamera(UsbDevice device) {
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
             android.hardware.usb.UsbInterface intf = device.getInterface(i);
             // USB_CLASS_VIDEO is 14 (0x0E)
             if (intf.getInterfaceClass() == 14) {
                 return true;
             }
        }
        return false;
    }

    private void discoverAndInitCameras() {
        mMainHandler.post(() -> {
            // Create a temporary helper to discover devices
            ICameraHelper tempHelper = new CameraHelper();
            tempHelper.setStateCallback(new ICameraHelper.StateCallback() {
                @Override
                public void onAttach(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, "Discovered device: " + device.getDeviceName());
                }

                @Override
                public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {}

                @Override
                public void onCameraOpen(UsbDevice device) {}

                @Override
                public void onCameraClose(UsbDevice device) {}

                @Override
                public void onDeviceClose(UsbDevice device) {}

                @Override
                public void onDetach(UsbDevice device) {}

                @Override
                public void onCancel(UsbDevice device) {}
            });

            List<UsbDevice> devices = tempHelper.getDeviceList();
            tempHelper.release();

            if (devices != null && !devices.isEmpty()) {
                Set<String> seenDevices = new HashSet<>();
                List<CameraItem> newItems = new ArrayList<>();

                for (UsbDevice device : devices) {
                    if (processNewDevice(device, false)) {
                         // Already added
                    }
                }
                
                if (!cameraItems.isEmpty()) {
                    cameraAdapter.setCameraItems(cameraItems);
                    // Start opening cameras sequentially
                    openCameraSequentially(0);
                }
            } else {
                if (DEBUG) Log.d(TAG, "No UVC devices found");
            }
        });
    }

    private void openCameraSequentially(int index) {
        if (index >= cameraItems.size()) {
            if (DEBUG) Log.d(TAG, "All cameras initialization sequence finished.");
            return;
        }
        
        CameraItem item = cameraItems.get(index);
        
        // Skip if already connected or having a helper (unless failed and we want automated retry, but let's stick to skip)
        if (item.isConnected() || item.getCameraHelper() != null) {
            if (DEBUG) Log.d(TAG, "Skipping already initialized camera: " + item.getDisplayName());
            openCameraSequentially(index + 1);
            return;
        }
        
        openCamera(item, index, () -> {
            // Proceed to next camera after delay
            mAsyncHandler.postDelayed(() -> openCameraSequentially(index + 1), 500);
        });
    }

    private void handleDeviceAttach(UsbDevice device) {
        mMainHandler.post(() -> {
            if (processNewDevice(device, true)) {
                cameraAdapter.setCameraItems(cameraItems);
                // Open new camera
                int position = cameraItems.size() - 1;
                CameraItem item = cameraItems.get(position);
                openCamera(item, position, null);
            }
        });
    }

    private void handleDeviceDetach(UsbDevice device) {
        mMainHandler.post(() -> {
            CameraItem itemToRemove = null;
            for (CameraItem item : cameraItems) {
                if (item.getDevice().equals(device)) {
                    itemToRemove = item;
                    break;
                }
            }
            
            if (itemToRemove != null) {
                if (DEBUG) Log.d(TAG, "Removing detached camera: " + itemToRemove.getDisplayName());
                
                // Release resources
                if (itemToRemove.getCameraHelper() != null) {
                    itemToRemove.getCameraHelper().release();
                }
                
                cameraItems.remove(itemToRemove);
                // Remove from map
                String keyToRemove = null;
                for (Map.Entry<String, CameraItem> entry : cameraItemMap.entrySet()) {
                    if (entry.getValue() == itemToRemove) {
                        keyToRemove = entry.getKey();
                        break;
                    }
                }
                if (keyToRemove != null) cameraItemMap.remove(keyToRemove);
                
                cameraAdapter.setCameraItems(cameraItems);
            }
        });
    }

    private boolean processNewDevice(UsbDevice device, boolean isUpdate) {
        try {
            // Filter non-camera devices if discovered via BroadcastReceiver
            if (isUpdate && !isUvcCamera(device)) return false;

            String deviceKey = getDeviceKey(device);
            String displayName = getDeviceDisplayName(device);
            
            // Skip Wireless_Device
            if ("Wireless_Device".equals(displayName)) return false;
            
            // Check duplicates
            if (cameraItemMap.containsKey(deviceKey)) return false;

            CameraItem item = new CameraItem(device, displayName);
            cameraItems.add(item);
            cameraItemMap.put(deviceKey, item);
            return true;
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Error processing device: " + e.getMessage(), e);
            return false;
        }
    }

    private String getDeviceKey(UsbDevice device) {
        // Use device path (bus/device number) as the primary unique identifier
        // This prevents duplicate entries for the same physical camera
        String deviceName = device.getDeviceName();
        if (deviceName != null && deviceName.contains("/")) {
            String[] parts = deviceName.split("/");
            if (parts.length >= 2) {
                // Use bus and device number (e.g., "003/012")
                String busNumber = parts[parts.length - 2];
                String deviceNumber = parts[parts.length - 1];
                return busNumber + "/" + deviceNumber;
            }
        }
        
        // Fallback to vendor/product ID + serial
        String serial = null;
        try {
            serial = device.getSerialNumber();
        } catch (SecurityException e) {
            // Some devices may not have permission to read serial number
            if (DEBUG) Log.w(TAG, "No permission to read serial in getDeviceKey");
        }
        
        String baseKey = device.getVendorId() + "_" + device.getProductId();
        
        if (serial != null && !serial.isEmpty()) {
            return baseKey + "_" + serial;
        }
        
        return baseKey + "_" + deviceName;
    }

    private String getDeviceDisplayName(UsbDevice device) {
        String name = device.getProductName();
        if (name == null || name.isEmpty()) {
            name = "Camera " + device.getDeviceId();
        }
        // Only return the name without vendor/product ID
        return name;
    }
    
    // Helper to run logic only once provided callback
    private static class OneShotRunnable implements Runnable {
        private final Runnable target;
        boolean ran = false;
        OneShotRunnable(Runnable target) { this.target = target; }
        @Override
        public synchronized void run() {
            if (!ran && target != null) {
                ran = true;
                target.run();
            }
        }
    }

    private void openCamera(CameraItem item, int position, Runnable onComplete) {
        mAsyncHandler.post(() -> {
            if (DEBUG) Log.d(TAG, "Opening camera: " + item.getDisplayName());
            
            ICameraHelper helper = new CameraHelper();
            item.setCameraHelper(helper);
            
            final Runnable completionCallback = onComplete != null ? new OneShotRunnable(onComplete) : null;
            
            helper.setStateCallback(new ICameraHelper.StateCallback() {
                @Override
                public void onAttach(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onAttach");
                }

                @Override
                public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onDeviceOpen");
                    if (device.equals(item.getDevice())) {
                        try {
                            UVCParam param = new UVCParam();
                            param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH);
                            helper.openCamera(param);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open camera: " + e.getMessage());
                            if (completionCallback != null) completionCallback.run();
                        }
                    }
                }

                @Override
                public void onCameraOpen(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onCameraOpen");
                    if (device.equals(item.getDevice())) {
                        selectMJPEGFormat(helper, item.getDisplayName());
                        helper.startPreview();
                        
                        Size size = helper.getPreviewSize();
                        if (size != null) {
                            if (DEBUG) Log.d(TAG, item.getDisplayName() + " Preview size: " + size.width + "x" + size.height);
                        }
                        
                        item.setConnected(true);
                        item.setFailed(false);
                        mMainHandler.post(() -> cameraAdapter.updateCameraItem(position));
                        
                        // Delay completion to ensure stable start
                        mAsyncHandler.postDelayed(() -> {
                             if (completionCallback != null) completionCallback.run();
                        }, 500);
                    }
                }

                @Override
                public void onCameraClose(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onCameraClose");
                    item.setConnected(false);
                    mMainHandler.post(() -> cameraAdapter.updateCameraItem(position));
                }

                @Override
                public void onDeviceClose(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onDeviceClose");
                }

                @Override
                public void onDetach(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onDetach");
                }

                @Override
                public void onCancel(UsbDevice device) {
                    if (DEBUG) Log.d(TAG, item.getDisplayName() + " onCancel - FAILED");
                    item.setFailed(true);
                    item.setConnected(false);
                    mMainHandler.post(() -> cameraAdapter.updateCameraItem(position));
                    
                    if (completionCallback != null) completionCallback.run();
                }
            });

            // Select the device immediately as we are on async thread
            helper.selectDevice(item.getDevice());

            // Add a timeout (10 seconds) to give up if camera doesn't open
            mAsyncHandler.postDelayed(() -> {
                if (completionCallback != null && !((OneShotRunnable)completionCallback).ran) {
                    if (DEBUG) Log.w(TAG, item.getDisplayName() + " open timeout - giving up");
                    item.setFailed(true);
                    item.setConnected(false);
                    mMainHandler.post(() -> cameraAdapter.updateCameraItem(position));
                    completionCallback.run();
                }
            }, 10000); // 10 second timeout
        });
    }

    private void selectMJPEGFormat(ICameraHelper helper, String logPrefix) {
        List<Format> supportedFormats = helper.getSupportedFormatList();
        if (supportedFormats != null) {
            Size bestSize = null;
            for (Format format : supportedFormats) {
                if (format.type == UVCCamera.UVC_VS_FORMAT_MJPEG) {
                    for (Format.Descriptor descriptor : format.frameDescriptors) {
                        List<Integer> fpsList = new ArrayList<>();
                        if (descriptor.intervals != null) {
                            for (Format.Interval interval : descriptor.intervals) {
                                fpsList.add(interval.fps);
                            }
                        }
                        int fps = 0;
                        if (!fpsList.isEmpty()) {
                            // Target 15 FPS to save bandwidth for multi-camera setup
                            int targetFps = 15;
                            int bestFps = fpsList.get(0);
                            int minDiff = Integer.MAX_VALUE;
                            
                            for (int f : fpsList) {
                                // Prefer FPS <= 30
                                if (f > 30) continue;
                                
                                int diff = Math.abs(f - targetFps);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    bestFps = f;
                                } else if (diff == minDiff) {
                                    // If diff is same, prefer lower FPS to save bandwidth
                                    if (f < bestFps) {
                                        bestFps = f;
                                    }
                                }
                            }
                            fps = bestFps;
                            if (DEBUG) Log.d(TAG, logPrefix + " Format: " + descriptor.width + "x" + descriptor.height + " selected FPS: " + fps);
                        }
                        
                        Size currentSize = new Size(descriptor.type, descriptor.width, descriptor.height, fps, fpsList);

                        // Prefer 640x480
                        if (descriptor.width == 640 && descriptor.height == 480) {
                            bestSize = currentSize;
                            break;
                        } else if (descriptor.width == 320 && descriptor.height == 240) {
                            // Keep looking for 640x480 but save this as fallback
                            if (bestSize == null || bestSize.width != 640) {
                                bestSize = currentSize;
                            }
                        } else if (bestSize == null) {
                            bestSize = currentSize;
                        }
                    }
                }
                if (bestSize != null && bestSize.width == 640) break;
            }
            if (bestSize != null) {
                if (DEBUG) Log.d(TAG, logPrefix + " Setting resolution to " + bestSize.width + "x" + bestSize.height);
                helper.setPreviewSize(bestSize);
            }
        }
    }

    @Override
    public void onRetryCamera(CameraItem item) {
        if (DEBUG) Log.d(TAG, "Retry camera: " + item.getDisplayName());
        
        // Release old helper
        if (item.getCameraHelper() != null) {
            item.getCameraHelper().release();
            item.setCameraHelper(null);
        }
        
        // Find position and retry
        int position = cameraItems.indexOf(item);
        if (position >= 0) {
            item.setFailed(false);
            cameraAdapter.updateCameraItem(position);
            mAsyncHandler.postDelayed(() -> {
                // Pass null for chaining as this is a single retry
                openCamera(item, position, null);
            }, 500);
        }
    }

    @Override
    public void onCloseCamera(CameraItem item) {
        if (DEBUG) Log.d(TAG, "Close camera: " + item.getDisplayName());
        
        // Release helper
        if (item.getCameraHelper() != null) {
            item.getCameraHelper().release();
            item.setCameraHelper(null);
        }
        
        // Remove from list and map
        int position = cameraItems.indexOf(item);
        if (position >= 0) {
            cameraItems.remove(position);
            // Remove from map
            String deviceKey = getDeviceKey(item.getDevice());
            cameraItemMap.remove(deviceKey);
            
            cameraAdapter.setCameraItems(cameraItems);
        }
    }

    private void releaseAllCameras() {
        if (DEBUG) Log.d(TAG, "Releasing all cameras");
        for (CameraItem item : cameraItems) {
            if (item.getCameraHelper() != null) {
                item.getCameraHelper().release();
                item.setCameraHelper(null);
            }
            item.setConnected(false);
        }
        cameraItems.clear();
        cameraItemMap.clear();
    }
}