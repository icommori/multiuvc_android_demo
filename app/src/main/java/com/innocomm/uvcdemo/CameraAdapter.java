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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.facemesh.FaceMesh;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.text.Text;
import com.innocomm.uvcdemo.ai.AIDetector;
import com.innocomm.uvcdemo.ai.AIManager;
import com.innocomm.uvcdemo.ai.AgeGenderResult;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.widget.AspectRatioSurfaceView;

import org.tensorflow.lite.task.vision.detector.Detection;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.CameraViewHolder> {
    
    private static final String TAG = "CameraAdapter";
    private static final boolean DEBUG = true;
    
    private List<CameraItem> cameraItems = new ArrayList<>();
    private OnCameraActionListener listener;
    private Context context;
    private int itemHeight;
    
    public interface OnCameraActionListener {
        void onRetryCamera(CameraItem item);
        void onCloseCamera(CameraItem item);
    }
    
    public void assignRandomAI(int position, AIManager.AIType type) {
        if (position >= 0 && position < cameraItems.size()) {
            // Find ViewHolder for this position? No, we update the data and notify
            // But we actually need to trigger the logic in ViewHolder which owns the handler
            // So we'll use notifyItemChanged with payload
            
            CameraItem item = cameraItems.get(position);
            item.setPendingAIType(type);
            notifyItemChanged(position, "START_AI");
        }
    }
    
    public CameraAdapter(OnCameraActionListener listener) {
        this.listener = listener;
        if (listener instanceof Context) {
            this.context = (Context) listener;
            calculateItemHeight();
        }
    }

    private android.view.Display externalDisplay = null;
    public void setExternalDisplay(android.view.Display display) {
        this.externalDisplay = display;
        notifyDataSetChanged();
    }
    
    private void calculateItemHeight() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(metrics);
        
        int orientation = context.getResources().getConfiguration().orientation;
        int screenHeight = metrics.heightPixels;
        
        // Get the action bar height
        int actionBarHeight = 0;
        int resourceId = context.getResources().getIdentifier("action_bar_size", "dimen", "android");
        if (resourceId > 0) {
            actionBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        
        // Get status bar height
        int statusBarHeight = 0;
        resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        
        // Calculate available height (subtract status bar, action bar/toolbar, and padding)
        int paddingDp = 4; // RecyclerView padding
        int paddingPx = (int) (paddingDp * metrics.density);
        
        // Use a fixed value for action bar if the identifier fails, but usually it works
        if (actionBarHeight == 0) {
            actionBarHeight = (int) (56 * metrics.density); // standard toolbar height
        }
        
        int availableHeight = screenHeight - statusBarHeight - actionBarHeight - (paddingPx * 2);
        
        // Divide by rows based on orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            itemHeight = availableHeight / 2; // 2 rows in landscape (2x3)
        } else {
            itemHeight = availableHeight / 3; // 3 rows in portrait (3x2)
        }
        
        if (DEBUG) Log.d(TAG, "Screen height: " + screenHeight + ", Available: " + availableHeight + ", Item height: " + itemHeight);
    }

    public void refreshConfig() {
        calculateItemHeight();
        notifyDataSetChanged();
    }
    
    public void addCameraItem(CameraItem item) {
        this.cameraItems.add(item);
        notifyItemInserted(cameraItems.size() - 1);
    }

    public void removeCameraItem(CameraItem item) {
        int position = cameraItems.indexOf(item);
        if (position >= 0) {
            cameraItems.remove(position);
            notifyItemRemoved(position);
        }
    }

    public List<CameraItem> getCameraItems() {
        return cameraItems;
    }

    public void clearCameraItems() {
        int size = cameraItems.size();
        cameraItems.clear();
        notifyItemRangeRemoved(0, size);
    }

    public void updateCameraItem(int position) {
        if (position >= 0 && position < cameraItems.size()) {
            notifyItemChanged(position);
        }
    }
    
    @NonNull
    @Override
    public CameraViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_camera_preview, parent, false);
        
        // Set the item height dynamically
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = itemHeight;
        view.setLayoutParams(params);
        
        return new CameraViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CameraViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if ("START_AI".equals(payload)) {
                    holder.triggerPendingAI();
                }
            }
        } else {
            holder.bind(cameraItems.get(position), position);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull CameraViewHolder holder, int position) {
        holder.bind(cameraItems.get(position), position);
    }
    
    @Override
    public int getItemCount() {
        return cameraItems.size();
    }
    
    @Override
    public void onViewRecycled(@NonNull CameraViewHolder holder) {
        super.onViewRecycled(holder);
        holder.cleanup();
    }
    


    class CameraViewHolder extends RecyclerView.ViewHolder implements SurfaceHolder.Callback {
        
        private AspectRatioSurfaceView svCameraView;
        private OverlayView overlayView; // Restored
        private TextView tvCameraName;
        private TextView tvFps;
        private ImageView ivRetry;
        private ProgressBar pbLoading;
        private ImageButton btnOptions;
        private ImageButton btnHdmiToggle;
        private ImageView ivProjecting;
        private CameraItem currentItem;
        
        // AI Threading
        private HandlerThread aiThread;
        private Handler aiHandler;
        private final AtomicBoolean isProcessing = new AtomicBoolean(false);

        // FPS calculation
        private final AtomicInteger frameCount = new AtomicInteger(0);
        private long lastFpsUpdateTime = 0;
        private double smoothedFps = -1;
        private final Runnable fpsUpdater = new Runnable() {
            @Override
            public void run() {
                if (tvFps != null && currentItem != null && currentItem.isConnected()) {
                    long now = System.currentTimeMillis();
                    int count = frameCount.getAndSet(0);
                    if (lastFpsUpdateTime > 0) {
                        long elapsed = now - lastFpsUpdateTime;
                        if (elapsed > 0) {
                            double currentFps = (count * 1000.0) / elapsed;
                            if (smoothedFps < 0) {
                                smoothedFps = currentFps;
                            } else {
                                smoothedFps = (smoothedFps * 0.7) + (currentFps * 0.3);
                            }
                            tvFps.setText(String.format(java.util.Locale.US, "FPS: %.0f", smoothedFps));
                        }
                    } else {
                        tvFps.setText("FPS: " + count);
                    }
                    lastFpsUpdateTime = now;
                    tvFps.postDelayed(this, 1000);
                }
            }
        };

        private final IFrameCallback frameCallback = new IFrameCallback() {
            @Override
            public void onFrame(ByteBuffer byteBuffer) {

                frameCount.incrementAndGet();
                if (byteBuffer != null && currentItem != null && currentItem.getCameraHelper() != null) {
                    // Check if AI needed
                    if (currentItem.getCurrentAIType() == AIManager.AIType.NONE) return;

                    // Flow control
                    if (isProcessing.get()) return;

                    Size previewSize = currentItem.getCameraHelper().getPreviewSize();
                    if (previewSize == null) return;

                    int width = previewSize.width;
                    int height = previewSize.height;
                    
                    // Mark as processing
                    isProcessing.set(true);
                    
                    try {
                        byteBuffer.rewind();
                        // Convert to Bitmap (this copies data, so it's safe to use in another thread)
                        // Note: yuvToBitmap is in Kotlin file com.innocomm.uvcdemo.UtilsKt
                        final Bitmap bitmap = UtilsKt.yuvToBitmap(byteBuffer, width, height);
                        //Log.v("innocomm","onFrame "+bitmap.getWidth()+"x"+bitmap.getHeight());

                        if (bitmap != null) {
                            if (aiHandler != null) {
                                aiHandler.post(() -> runAIDetection(bitmap, width, height));
                            } else {
                                bitmap.recycle();
                                isProcessing.set(false);
                            }
                        } else {
                            isProcessing.set(false);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Frame conversion error", e);
                        isProcessing.set(false);
                    }
                }
            }
        };
        
        private void runAIDetection(Bitmap bitmap, int width, int height) {
            if (currentItem == null || !currentItem.isConnected()) {
                bitmap.recycle();
                isProcessing.set(false);
                return;
            }

            AIDetector detector = currentItem.getCurrentDetector();
            if (detector == null) {
                bitmap.recycle();
                isProcessing.set(false);
                return;
            }

            try {
                // Perform detection
                detector.detect(bitmap, result -> {
                    // Update UI on main thread
                    if (overlayView != null) {
                        overlayView.post(() -> {
                            if (result == null) {
                                if (overlayView != null) overlayView.clear();
                                if (currentItem != null && currentItem.getDisplayPresentation() != null) {
                                    currentItem.getDisplayPresentation().getOverlayView().clear();
                                }
                            } else {
                                updateOverlay(result, height, width); // height/width might need swapping depending on rotation, but usually standard
                            }
                        });
                    }
                    
                    bitmap.recycle();
                    isProcessing.set(false);
                });
            } catch (Exception e) {
                Log.e(TAG, "Detection error", e);
                bitmap.recycle();
                isProcessing.set(false);
            }
        }

        private void updateOverlay(Object result, int height, int width) {
             OverlayView targetOverlayView = (currentItem != null && currentItem.isProjecting() && currentItem.getDisplayPresentation() != null) 
                 ? currentItem.getDisplayPresentation().getOverlayView() : this.overlayView;
                 
             if (targetOverlayView == null) return;
             
             if (result instanceof List) {
                List<?> list = (List<?>) result;
                if (!list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Detection) {
                        targetOverlayView.setResults((List<Detection>) result, height, width);
                    } else if (first instanceof Face) {
                        targetOverlayView.setFaceResults((List<Face>) result, height, width);
                    } else if (first instanceof FaceMesh) {
                        targetOverlayView.setMeshResults((List<FaceMesh>) result, height, width);
                    } else if (first instanceof Barcode) {
                        targetOverlayView.setBarcodeResults((List<Barcode>) result, height, width);
                    } else if (first instanceof AgeGenderResult) {
                        targetOverlayView.setAgeGenderResults((List<AgeGenderResult>) result, height, width);
                    }
                } else {
                    targetOverlayView.clear();
                }
            } else if (result instanceof Pose) {
                targetOverlayView.setPoseResults((Pose) result, height, width);
            } else if (result instanceof Text) {
                targetOverlayView.setTextResults((Text) result, height, width);
            }
        }
        
        public CameraViewHolder(@NonNull View itemView) {
            super(itemView);
            svCameraView = itemView.findViewById(R.id.svCameraView);
            overlayView = itemView.findViewById(R.id.overlayView); // Restored
            tvCameraName = itemView.findViewById(R.id.tvCameraName);
            tvFps = itemView.findViewById(R.id.tvFps);
            ivRetry = itemView.findViewById(R.id.ivRetry);
            pbLoading = itemView.findViewById(R.id.pbLoading);
            btnOptions = itemView.findViewById(R.id.btnOptions);
            btnHdmiToggle = itemView.findViewById(R.id.btnHdmiToggle);
            ivProjecting = itemView.findViewById(R.id.ivProjecting);
            
            svCameraView.setAspectRatio(640, 480);
            svCameraView.getHolder().addCallback(this);
        }
        
        public void bind(CameraItem item, int position) {
            boolean sameItem = (currentItem == item);

            // Remove surface and callback from old helper if exists and it is a DIFFERENT item
            if (currentItem != null && currentItem.getCameraHelper() != null && !sameItem) {
                currentItem.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
                currentItem.getCameraHelper().setFrameCallback(null, 0);
            }
            
            // Update item height
            ViewGroup.LayoutParams params = itemView.getLayoutParams();
            if (params.height != itemHeight) {
                params.height = itemHeight;
                itemView.setLayoutParams(params);
            }
            
            // Stop FPS updates and reset stats
            if (tvFps != null) tvFps.removeCallbacks(fpsUpdater);
            frameCount.set(0);
            lastFpsUpdateTime = 0;
            smoothedFps = -1;
            
            currentItem = item;
            updateTitle(item);
            
            // Start AI Thread if needed
            startAIThread();
            
            // Restore AI Detector if persistent state exists but detector was released (e.g. during recycle)
            if (item.getCurrentAIType() != AIManager.AIType.NONE && item.getCurrentDetector() == null) {
                 if (aiHandler != null) {
                     aiHandler.post(() -> {
                         // Double check validity inside thread
                         if (currentItem == item && item.getCurrentAIType() != AIManager.AIType.NONE && item.getCurrentDetector() == null) {
                             if (DEBUG) Log.d(TAG, "Restoring AI detector for recycled view: " + item.getDisplayName());
                             AIDetector detector = AIManager.getInstance(context).getDetector(item.getCurrentAIType(), item.getDeviceKey());
                             if (detector != null) {
                                item.setCurrentDetector(detector);
                                // Update title to reflect ready state
                                if (tvCameraName != null) {
                                    tvCameraName.post(() -> updateTitle(item));
                                }
                             }
                         }
                     });
                 }
            }
            
            // Check pending AI
            if (item.getPendingAIType() != AIManager.AIType.NONE) {
                triggerPendingAI();
            }
            
            // Reset Overlay
            if (overlayView != null) overlayView.clear();
            if (item != null && item.getDisplayPresentation() != null) {
                item.getDisplayPresentation().getOverlayView().clear();
            }
            
            // ... (rest of bind) ...
            
            // ... (rest of bind) ...

            if (item.isFailed()) {
                ivRetry.setVisibility(View.VISIBLE);
                pbLoading.setVisibility(View.GONE);
                btnOptions.setVisibility(View.GONE);
                tvFps.setVisibility(View.GONE);
                ivRetry.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onRetryCamera(item);
                    }
                });
            } else if (item.isConnected()) {
                ivRetry.setVisibility(View.GONE);
                pbLoading.setVisibility(View.GONE);
                btnOptions.setVisibility(View.VISIBLE);
                
                setupOptionsButton(item);
                setupHdmiToggle(item);
                updateHdmiUI(item);
                
                // Update aspect ratio from helper if available
                if (item.getCameraHelper() != null) {
                    Size size = item.getCameraHelper().getPreviewSize();
                    if (size != null) {
                        svCameraView.setAspectRatio(size.width, size.height);
                    }
                }
                
                // Start FPS updates
                tvFps.setVisibility(View.VISIBLE);
                tvFps.post(fpsUpdater);
                
                if (svCameraView.getHolder().getSurface() != null && svCameraView.getHolder().getSurface().isValid()) {
                     if (item.getCameraHelper() != null && !item.isPaused() && !item.isProjecting()) {
                        item.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
                        item.getCameraHelper().addSurface(svCameraView.getHolder().getSurface(), false);
                        // Add frame callback - Consolidate format to YUYV
                        item.getCameraHelper().setFrameCallback(frameCallback, UVCCamera.FRAME_FORMAT_YUYV);
                        svCameraView.post(() -> updateTitle(item));
                     }
                }

            } else {
                ivRetry.setVisibility(View.GONE);
                pbLoading.setVisibility(View.VISIBLE);
                btnOptions.setVisibility(View.GONE);
                tvFps.setVisibility(View.GONE);
            }
        }
        
        private void startAIThread() {
             if (aiThread == null) {
                 aiThread = new HandlerThread("AIThread-" + this.hashCode());
                 aiThread.start();
                 aiHandler = new Handler(aiThread.getLooper());
             }
        }

        private void stopAIThread() {
             if (aiThread != null) {
                 aiThread.quitSafely();
                 try {
                     aiThread.join(500);
                 } catch (InterruptedException e) {}
                 aiThread = null;
                 aiHandler = null;
             }
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (currentItem == null) return;
            if (DEBUG) Log.d(TAG, "surfaceCreated for " + currentItem.getDisplayName());
            if (currentItem.getCameraHelper() != null && currentItem.isConnected() && !currentItem.isPaused() && !currentItem.isProjecting()) {
                currentItem.getCameraHelper().addSurface(holder.getSurface(), false);
                // Add frame callback
                currentItem.getCameraHelper().setFrameCallback(frameCallback, UVCCamera.FRAME_FORMAT_YUYV);
                svCameraView.post(() -> updateTitle(currentItem));
            }
        }
        
        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        }
        
        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            if (currentItem == null) return;
            if (DEBUG) Log.d(TAG, "surfaceDestroyed for " + currentItem.getDisplayName());
            if (currentItem.getCameraHelper() != null) {
                currentItem.getCameraHelper().removeSurface(holder.getSurface());
            }
            if (tvFps != null) tvFps.removeCallbacks(fpsUpdater);
        }
        
        private void setupOptionsButton(CameraItem item) {
            btnOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, btnOptions);
                popup.getMenu().add(0, 1, 0, "Change Resolution");
                popup.getMenu().add(0, 4, 0, "AI Detect (" + (item.getCurrentAIType() == AIManager.AIType.NONE ? "Off" : item.getCurrentAIType().getDisplayName()) + ") >");
                popup.getMenu().add(0, 2, 0, "Restart Camera");
                popup.getMenu().add(0, 3, 0, "Close");
                
                popup.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case 1:
                            showResolutionDialog(item);
                            return true;
                        case 4:
                            showAIMenu(item);
                            return true;
                        case 2:
                            if (listener != null) {
                                listener.onRetryCamera(item);
                            }
                            return true;
                        case 3:
                            if (listener != null) {
                                listener.onCloseCamera(item);
                            }
                            return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

        
        private void setupHdmiToggle(CameraItem item) {
            if (externalDisplay != null) {
                btnHdmiToggle.setVisibility(View.VISIBLE);
                btnHdmiToggle.setOnClickListener(v -> {
                    if (item.isProjecting()) {
                        stopProjection(item);
                    } else {
                        startProjection(item);
                    }
                    notifyDataSetChanged();
                });
            } else {
                btnHdmiToggle.setVisibility(View.GONE);
                if (item.isProjecting()) {
                    stopProjection(item);
                }
            }
        }

        private void startProjection(CameraItem item) {
            if (externalDisplay == null || item.getCameraHelper() == null) return;
            // First stop any other projecting items
            for (CameraItem ci : cameraItems) {
                if (ci.isProjecting() && ci != item) {
                    ci.setProjecting(false);
                    if (ci.getDisplayPresentation() != null) {
                        ci.getDisplayPresentation().dismiss();
                        ci.setDisplayPresentation(null);
                    }
                }
            }
            item.setProjecting(true);
            HdmiPresentation presentation = new HdmiPresentation(context, externalDisplay);
            item.setDisplayPresentation(presentation);
            presentation.show();
            
            presentation.getCameraView().getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    if (item.getCameraHelper() != null) {
                        try {
                            item.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
                        } catch(Exception e){}
                        item.getCameraHelper().addSurface(holder.getSurface(), false);
                        Size size = item.getCameraHelper().getPreviewSize();
                        if (size != null) {
                            presentation.getCameraView().setAspectRatio(size.width, size.height);
                        }
                    }
                }
                @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
                @Override public void surfaceDestroyed(SurfaceHolder holder) {
                    if (item.getCameraHelper() != null) {
                        item.getCameraHelper().removeSurface(holder.getSurface());
                    }
                }
            });
            updateHdmiUI(item);
        }

        private void stopProjection(CameraItem item) {
            item.setProjecting(false);
            if (item.getDisplayPresentation() != null) {
                if (item.getDisplayPresentation().getOverlayView() != null) {
                     item.getDisplayPresentation().getOverlayView().clear();
                }
                if (item.getCameraHelper() != null && item.getDisplayPresentation().getCameraView().getHolder().getSurface() != null) {
                    item.getCameraHelper().removeSurface(item.getDisplayPresentation().getCameraView().getHolder().getSurface());
                }
                item.getDisplayPresentation().dismiss();
                item.setDisplayPresentation(null);
            }
            
            // Reconnect back to original surface
            if (item.getCameraHelper() != null && svCameraView.getHolder().getSurface() != null && svCameraView.getHolder().getSurface().isValid()) {
                try {
                    item.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
                } catch(Exception e){}
                item.getCameraHelper().addSurface(svCameraView.getHolder().getSurface(), false);
                item.getCameraHelper().setFrameCallback(frameCallback, UVCCamera.FRAME_FORMAT_YUYV);
            }
            updateHdmiUI(item);
        }

        private void updateHdmiUI(CameraItem item) {
            if (item.isProjecting()) {
                btnHdmiToggle.setColorFilter(android.graphics.Color.GREEN);
                svCameraView.setVisibility(View.INVISIBLE);
                overlayView.setVisibility(View.INVISIBLE);
                ivProjecting.setVisibility(View.VISIBLE);
            } else {
                btnHdmiToggle.clearColorFilter();
                svCameraView.setVisibility(View.VISIBLE);
                overlayView.setVisibility(View.VISIBLE);
                ivProjecting.setVisibility(View.GONE);
            }
        }

        private void showResolutionDialog(CameraItem item) {
            if (item == null || item.getCameraHelper() == null) return;
            List<Format> formats = item.getCameraHelper().getSupportedFormatList();
            if (formats == null || formats.isEmpty()) {
                Toast.makeText(context, "No formats available", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> sizeStrings = new ArrayList<>();
            List<Size> sizes = new ArrayList<>();

            for (Format format : formats) {
                if (format.type == UVCCamera.UVC_VS_FORMAT_MJPEG) {
                    for (Format.Descriptor descriptor : format.frameDescriptors) {
                         boolean exists = false;
                         for (Size s : sizes) {
                             if (s.width == descriptor.width && s.height == descriptor.height) {
                                 exists = true;
                                 break;
                             }
                         }
                         if (!exists) {
                             sizes.add(new Size(descriptor.type, descriptor.width, descriptor.height, 30, new ArrayList<>()));
                             sizeStrings.add(descriptor.width + "x" + descriptor.height);
                         }
                    }
                }
            }
            
            if (sizes.isEmpty()) {
                 Toast.makeText(context, "No MJPEG formats", Toast.LENGTH_SHORT).show();
                 return;
            }

            new androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Select Resolution")
                .setItems(sizeStrings.toArray(new String[0]), (dialog, which) -> {
                    Size selected = sizes.get(which);
                    if (DEBUG) Log.d(TAG, "Selected resolution: " + selected.width + "x" + selected.height);
                    
                    if (item.getCameraHelper() != null) {
                        item.getCameraHelper().stopPreview();
                        item.getCameraHelper().setPreviewSize(selected);
                        item.getCameraHelper().startPreview();
                        
                        // Update aspect ratio of SurfaceView
                        if (svCameraView != null) {
                            svCameraView.setAspectRatio(selected.width, selected.height);
                        }
                        
                        // Re-register frame callback with the new resolution
                        item.getCameraHelper().setFrameCallback(frameCallback, UVCCamera.FRAME_FORMAT_YUYV);
                        
                        // Update title IMMEDIATELY using the selected resolution to avoid delay/stale values
                        updateTitle(item, selected.width, selected.height);
                        
                        // Still post a sync update later just in case library state takes time to settle
                        tvCameraName.postDelayed(() -> updateTitle(item), 1000);
                    }
                })
                .show();
        }
        
        private void showAIMenu(CameraItem item) {
            PopupMenu aiPopup = new PopupMenu(context, btnOptions);
            AIManager aiManager = AIManager.getInstance(context);
            String userId = item.getDeviceKey();

            // None option
            aiPopup.getMenu().add(1, 0, 0, "None");

            // AI Options
            AIManager.AIType[] types = AIManager.AIType.values();
            for (int i = 1; i < types.length; i++) {
                AIManager.AIType type = types[i];
                boolean isBusy = aiManager.isAIBusy(type, userId);
                String title = type.getDisplayName() + (isBusy ? " (In Use)" : "");
                aiPopup.getMenu().add(1, i, i, title).setEnabled(!isBusy);
            }

            aiPopup.setOnMenuItemClickListener(menuItem -> {
                int index = menuItem.getItemId();
                if (index < 0 || index >= AIManager.AIType.values().length) return false;
                
                AIManager.AIType selectedType = AIManager.AIType.values()[index];
                if (DEBUG) Log.d(TAG, "AI Mode selected: " + selectedType);
                
                if (selectedType == item.getCurrentAIType()) return true;

                // Perform switch on AI thread to avoid race with detection
                if (aiHandler != null) {
                    // Show a toast or loading indicator if needed?
                    
                    aiHandler.post(() -> {
                         // Stop previous AI
                        if (item.getCurrentAIType() != AIManager.AIType.NONE) {
                            if (DEBUG) Log.d(TAG, "Releasing previous AI: " + item.getCurrentAIType());
                            aiManager.releaseDetector(item.getCurrentAIType(), userId);
                            item.setCurrentAIType(AIManager.AIType.NONE);
                            item.setCurrentDetector(null);
                            if (overlayView != null) overlayView.post(() -> overlayView.clear());
                            
                            // Force reset processing flag to prevent stuck state
                            isProcessing.set(false);
                        }

                        // Start new AI
                        if (selectedType != AIManager.AIType.NONE) {
                            if (DEBUG) Log.d(TAG, "Getting new detector for: " + selectedType);
                            // Force reset processing flag before starting new AI
                            isProcessing.set(false);
                            
                            AIDetector detector = aiManager.getDetector(selectedType, userId);
                            if (detector != null) {
                                item.setCurrentAIType(selectedType);
                                item.setCurrentDetector(detector);
                            } else {
                                if (context != null) {
                                    new Handler(context.getMainLooper()).post(() -> 
                                        Toast.makeText(context, "AI is currently in use by another camera", Toast.LENGTH_SHORT).show()
                                    );
                                }
                            }
                        }
                        
                        // Update UI
                        if (tvCameraName != null) {
                            tvCameraName.post(() -> updateTitle(item));
                        }
                    });
                }
                
                return true;
            });
            aiPopup.show();
        }

        private void updateTitle(CameraItem item) {
            updateTitle(item, -1, -1);
        }

        private void updateTitle(CameraItem item, int manualWidth, int manualHeight) {
            if (item == null || tvCameraName == null) return;
            
            String displayName = item.getDisplayName();
            final StringBuilder titleBuilder = new StringBuilder(displayName);
            
            if (manualWidth > 0 && manualHeight > 0) {
                // Use manually provided resolution
                titleBuilder.append(" - ").append(manualWidth).append("x").append(manualHeight);
            } else if (item.isConnected() && item.getCameraHelper() != null) {
                Size size = item.getCameraHelper().getPreviewSize();
                if (size != null) {
                    titleBuilder.append(" - ").append(size.width).append("x").append(size.height);
                }
            }
            
            if (item.getCurrentAIType() != AIManager.AIType.NONE) {
                 titleBuilder.append(" [").append(item.getCurrentAIType().getDisplayName()).append("]");
            }
            
            final String finalTitle = titleBuilder.toString();
            tvCameraName.post(() -> tvCameraName.setText(finalTitle));
        }

        public void cleanup() {
            if (DEBUG) Log.d(TAG, "cleanup ViewHolder");

            // Remove surface from helper if exists
            if (currentItem != null && currentItem.getCameraHelper() != null) {
                currentItem.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
            }
            currentItem = null;
        }

        public void cleanupAIjob() {
            if (DEBUG) Log.d(TAG, "onDetached()");
            isProcessing.set(true); // Stop processing

            // Release AI resources (but KEEP persistent state for recycle)
            if (currentItem != null && currentItem.getCurrentAIType() != AIManager.AIType.NONE) {
                if (DEBUG) Log.d(TAG, "Releasing detector for view recycle: " + currentItem.getDisplayName());
                AIManager.getInstance(context).releaseDetector(currentItem.getCurrentAIType(), currentItem.getDeviceKey());
                // Do NOT reset AIType here so it can be restored in bind()
                currentItem.setCurrentDetector(null);
            }
            if (currentItem != null && currentItem.getCameraHelper() != null) {
                currentItem.getCameraHelper().setFrameCallback(null, 0);
            }

            if (tvFps != null) tvFps.removeCallbacks(fpsUpdater);

            stopAIThread();
        }
        public void triggerPendingAI() {
            if (currentItem != null && currentItem.getPendingAIType() != AIManager.AIType.NONE) {
                AIManager.AIType pendingType = currentItem.getPendingAIType();
                // Consume
                currentItem.setPendingAIType(AIManager.AIType.NONE);
                
                if (aiHandler != null) {
                    aiHandler.post(() -> {
                        // Release previous if any
                        if (currentItem.getCurrentAIType() != AIManager.AIType.NONE) {
                             if (DEBUG) Log.d(TAG, "Releasing previous AI for pending: " + currentItem.getCurrentAIType());
                             AIManager.getInstance(context).releaseDetector(currentItem.getCurrentAIType(), currentItem.getDeviceKey());
                             currentItem.setCurrentAIType(AIManager.AIType.NONE);
                             currentItem.setCurrentDetector(null);
                             isProcessing.set(false);
                             if (overlayView != null) overlayView.post(() -> overlayView.clear());
                        }
                        
                         // Force reset processing flag
                        isProcessing.set(false);

                        if (DEBUG) Log.d(TAG, "Starting pending AI: " + pendingType);
                        AIDetector detector = AIManager.getInstance(context).getDetector(pendingType, currentItem.getDeviceKey());
                        if (detector != null) {
                            currentItem.setCurrentAIType(pendingType);
                            currentItem.setCurrentDetector(detector);
                        } else {
                            if (DEBUG) Log.w(TAG, "Could not get detector for pending AI: " + pendingType);
                        }
                        
                         // Update UI
                        if (tvCameraName != null) {
                            tvCameraName.post(() -> updateTitle(currentItem));
                        }
                    });
                }
            }
        }

    }
}
