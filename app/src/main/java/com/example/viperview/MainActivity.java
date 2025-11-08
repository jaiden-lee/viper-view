package com.example.viperview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;


import com.example.viperview.camera.CameraController;
import com.example.viperview.permissions.PermissionManager;
import com.example.viperview.yolo.PoseDetector;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private CameraController cameraController;
    private PermissionManager permissionManager;

    private ImageView leftImage;
    private ImageView rightImage;
    private PoseDetector poseDetector;
    private final java.util.concurrent.ExecutorService inferExec =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main_image_view);

        hideSystemUI();

        cameraController = new CameraController(this);
        permissionManager = new PermissionManager(this);
        try {
            poseDetector = new PoseDetector(getAssets(), "yolo11n-pose_float16.tflite");
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }

        defineImageViews();

        if (permissionManager.allPermissionsGranted()) {
            startCapturing();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    PermissionManager.REQUIRED_PERMISSIONS,
                    PermissionManager.REQUEST_CODE_PERMISSIONS
            );
        }
    }

    private void startCapturing() {
        cameraController.startFrameCapture(frame -> {
            if (isProcessing.get()) return; // drop if busy
            isProcessing.set(true);

            inferExec.execute(() -> {
                try {
                    float[][][] detections = poseDetector.run(frame);
                    Bitmap result = poseDetector.drawSkeleton(frame, detections);

                    runOnUiThread(() -> {
                        leftImage.setImageBitmap(result);
                        rightImage.setImageBitmap(result);
                    });
                } finally {
                    isProcessing.set(false);
                }
            });
        });
    }



    private void defineImageViews() {
        leftImage = findViewById(R.id.leftImage);
        rightImage = findViewById(R.id.rightImage);

        float shift = getResources().getDisplayMetrics().density * 25; // 20dp
        leftImage.setTranslationX(shift);
        rightImage.setTranslationX(-shift);
    }

    private void hideSystemUI() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.REQUEST_CODE_PERMISSIONS) {
            if (permissionManager.allPermissionsGranted()) {
                startCapturing();
            } else {
                finish(); // Exit if denied
            }
        }
    }
}
