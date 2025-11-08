package com.example.viperview.camera;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.util.SizeF;

import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class CameraController {

    private final Context context;

    public CameraController(Context context) {
        this.context = context;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public void startCamera(Preview leftPreview, Preview rightPreview,
                            androidx.camera.view.PreviewView leftView,
                            androidx.camera.view.PreviewView rightView) {

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Try to select ultrawide if available
                String ultraWideId = findUltraWideCameraId();
                CameraSelector cameraSelector;

                if (ultraWideId != null) {
                    cameraSelector = new CameraSelector.Builder()
                            .addCameraFilter(cameras -> {
                                for (androidx.camera.core.CameraInfo info : cameras) {
                                    String id = Camera2CameraInfo.from(info).getCameraId();
                                    if (id.equals(ultraWideId)) {
                                        return Collections.singletonList(info);
                                    }
                                }
                                return cameras;
                            })
                            .build();
                } else {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                }

                // Unbind before rebinding
                cameraProvider.unbindAll();

                leftPreview.setSurfaceProvider(leftView.getSurfaceProvider());
                rightPreview.setSurfaceProvider(rightView.getSurfaceProvider());

                cameraProvider.bindToLifecycle(
                        (androidx.lifecycle.LifecycleOwner) context,
                        cameraSelector,
                        leftPreview,
                        rightPreview
                );

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private String findUltraWideCameraId() {
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        String bestId = null;
        float maxFov = 0f;

        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraMetadata.LENS_FACING_BACK) {
                    float[] focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focalLengths != null && focalLengths.length > 0) {
                        float focal = focalLengths[0];
                        float sensorWidth = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE).getWidth();
                        float fov = 2 * (float) Math.atan(sensorWidth / (2 * focal));

                        if (fov > maxFov) {
                            maxFov = fov;
                            bestId = id;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bestId;
    }
}
