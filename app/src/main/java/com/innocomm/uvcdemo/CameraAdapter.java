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

import com.herohan.uvcapp.ICameraHelper;
import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.widget.AspectRatioSurfaceView;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.UVCCamera;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    
    public CameraAdapter(OnCameraActionListener listener) {
        this.listener = listener;
        if (listener instanceof Context) {
            this.context = (Context) listener;
            calculateItemHeight();
        }
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
        
        // Calculate available height (subtract status bar, action bar, and padding)
        int paddingDp = 4; // RecyclerView padding
        int paddingPx = (int) (paddingDp * metrics.density);
        int availableHeight = screenHeight - statusBarHeight - actionBarHeight - (paddingPx * 2);
        
        // Divide by 3 rows
        itemHeight = availableHeight / 3;
        
        if (DEBUG) Log.d(TAG, "Screen height: " + screenHeight + ", Available: " + availableHeight + ", Item height: " + itemHeight);
    }
    
    public void setCameraItems(List<CameraItem> items) {
        this.cameraItems = items;
        notifyDataSetChanged();
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
    public void onBindViewHolder(@NonNull CameraViewHolder holder, int position) {
        CameraItem item = cameraItems.get(position);
        holder.bind(item, position);
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
        private TextView tvCameraName;
        private TextView tvFps;
        private ImageView ivRetry;
        private ProgressBar pbLoading;
        private ImageButton btnOptions;
        private CameraItem currentItem;
        
        // FPS calculation variables
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
                            // Smoothing (EMA)
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
            public void onFrame(ByteBuffer frame) {
                frameCount.incrementAndGet();
            }
        };
        
        public CameraViewHolder(@NonNull View itemView) {
            super(itemView);
            svCameraView = itemView.findViewById(R.id.svCameraView);
            tvCameraName = itemView.findViewById(R.id.tvCameraName);
            tvFps = itemView.findViewById(R.id.tvFps);
            ivRetry = itemView.findViewById(R.id.ivRetry);
            pbLoading = itemView.findViewById(R.id.pbLoading);
            btnOptions = itemView.findViewById(R.id.btnOptions);
            
            svCameraView.setAspectRatio(640, 480);
            svCameraView.getHolder().addCallback(this);
        }
        
        public void bind(CameraItem item, int position) {
            // Remove surface and callback from old helper if exists
            if (currentItem != null && currentItem.getCameraHelper() != null) {
                currentItem.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
                currentItem.getCameraHelper().setFrameCallback(null, 0);
            }
            
            // Stop FPS updates and reset stats
            if (tvFps != null) tvFps.removeCallbacks(fpsUpdater);
            frameCount.set(0);
            lastFpsUpdateTime = 0;
            smoothedFps = -1;
            
            currentItem = item;
            updateTitle(item);
            
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
                
                // Start FPS updates
                tvFps.setVisibility(View.VISIBLE);
                tvFps.post(fpsUpdater);
                
                // If surface is already created (view reused), add it to the new helper
                if (svCameraView.getHolder().getSurface() != null && svCameraView.getHolder().getSurface().isValid()) {
                     if (item.getCameraHelper() != null && !item.isPaused()) {
                        item.getCameraHelper().addSurface(svCameraView.getHolder().getSurface(), false);
                        // Add frame callback
                        item.getCameraHelper().setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RAW);
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

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            if (currentItem == null) return;
            if (DEBUG) Log.d(TAG, "surfaceCreated for " + currentItem.getDisplayName());
            if (currentItem.getCameraHelper() != null && currentItem.isConnected() && !currentItem.isPaused()) {
                currentItem.getCameraHelper().addSurface(holder.getSurface(), false);
                // Add frame callback
                currentItem.getCameraHelper().setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RAW);
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

        private void updateTitle(CameraItem item) {
            if (item == null) return;
            String title = item.getDisplayName();
            if (item.isConnected() && item.getCameraHelper() != null) {
                // Check if preview size is available from helper
                Size size = item.getCameraHelper().getPreviewSize();
                if (size != null) {
                    title += " - " + size.width + "x" + size.height;
                }
            }
            tvCameraName.setText(title);
        }

        private void setupOptionsButton(CameraItem item) {
            btnOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(context, btnOptions);
                popup.getMenu().add(0, 1, 0, "Change Resolution");
                popup.getMenu().add(0, 2, 0, "Restart Camera");
                popup.getMenu().add(0, 3, 0, "Close");
                
                popup.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case 1:
                            showResolutionDialog(item);
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
        
        private void showResolutionDialog(CameraItem item) {
            ICameraHelper helper = item.getCameraHelper();
            if (helper == null) return;
            
            List<Format> supportedFormats = helper.getSupportedFormatList();
            if (supportedFormats == null || supportedFormats.isEmpty()) return;
            
            // Extract unique sizes from formats
            List<Size> supportedSizes = new ArrayList<>();
            for (Format format : supportedFormats) {
                // Determine format type
                if (format.type == 4 || format.type == 6) { // MJPEG or YUY2 usually
                     for (Format.Descriptor descriptor : format.frameDescriptors) {
                        List<Integer> fpsList = new ArrayList<>();
                        if (descriptor.intervals != null) {
                            for (Format.Interval interval : descriptor.intervals) {
                                fpsList.add(interval.fps);
                            }
                        }
                        int fps = fpsList.isEmpty() ? 0 : fpsList.get(0);
                        Size size = new Size(descriptor.type, descriptor.width, descriptor.height, fps, fpsList);
                         
                         boolean exists = false;
                         for (Size s : supportedSizes) {
                             if (s.width == size.width && s.height == size.height) {
                                 exists = true;
                                 break;
                             }
                         }
                         if (!exists) supportedSizes.add(size);
                     }
                }
            }
            
            if (supportedSizes.isEmpty()) {
                 // Fallback if type check failed
                 for (Format format : supportedFormats) {
                    for (Format.Descriptor descriptor : format.frameDescriptors) {
                        List<Integer> fpsList = new ArrayList<>();
                        if (descriptor.intervals != null) {
                            for (Format.Interval interval : descriptor.intervals) {
                                fpsList.add(interval.fps);
                            }
                        }
                        int fps = fpsList.isEmpty() ? 0 : fpsList.get(0);
                        Size size = new Size(descriptor.type, descriptor.width, descriptor.height, fps, fpsList);

                         boolean exists = false;
                         for (Size s : supportedSizes) {
                             if (s.width == size.width && s.height == size.height) {
                                 exists = true;
                                 break;
                             }
                         }
                         if (!exists) supportedSizes.add(size);
                    }
                 }
            }

            // Sort sizes
            Collections.sort(supportedSizes, (o1, o2) -> {
                int area1 = o1.width * o1.height;
                int area2 = o2.width * o2.height;
                return Integer.compare(area2, area1); // Descending
            });

            PopupMenu sizePopup = new PopupMenu(context, btnOptions);
            for (int i = 0; i < supportedSizes.size(); i++) {
                Size size = supportedSizes.get(i);
                sizePopup.getMenu().add(1, i, i, size.width + "x" + size.height);
            }
            
            sizePopup.setOnMenuItemClickListener(menuItem -> {
                int index = menuItem.getItemId();
                if (index >= 0 && index < supportedSizes.size()) {
                    Size selectedSize = supportedSizes.get(index);
                    changeResolution(item, selectedSize);
                    return true;
                }
                return false;
            });
            sizePopup.show();
        }
        
        private void changeResolution(CameraItem item, Size size) {
            ICameraHelper helper = item.getCameraHelper();
            if (helper != null) {
                if (!item.isPaused()) {
                    helper.stopPreview();
                }
                helper.setPreviewSize(size);
                svCameraView.setAspectRatio(size.width, size.height);
                if (!item.isPaused()) {
                    helper.startPreview();
                    // Re-add frame callback after restart? usually needed
                    helper.setFrameCallback(frameCallback, UVCCamera.PIXEL_FORMAT_RAW);
                }
                updateTitle(item);
                Toast.makeText(context, "Resolution changed to " + size.width + "x" + size.height, Toast.LENGTH_SHORT).show();
            }
        }
        

        
        public void cleanup() {
            if (DEBUG) Log.d(TAG, "cleanup ViewHolder");
            // Remove surface from helper if exists
            if (currentItem != null && currentItem.getCameraHelper() != null) {
                currentItem.getCameraHelper().removeSurface(svCameraView.getHolder().getSurface());
                // Remove frame callback 
                // currentItem.getCameraHelper().setFrameCallback(null, 0);
            }
            if (tvFps != null) tvFps.removeCallbacks(fpsUpdater);
            currentItem = null;
        }
    }
}
