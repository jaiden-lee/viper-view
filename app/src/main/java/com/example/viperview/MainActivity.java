package com.example.viperview;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.viperview.camera.CameraController;
import com.example.viperview.permissions.PermissionManager;
import com.example.viperview.yolo.PoseDetector;
import com.example.viperview.camera_stream.CameraStream;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private CameraController cameraController;
    private PermissionManager permissionManager;
    private CameraStream cameraStream;

    private ImageView leftImage;
    private ImageView rightImage;
    private PoseDetector poseDetector;

    private final java.util.concurrent.ExecutorService inferExec = java.util.concurrent.Executors
            .newSingleThreadExecutor();

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
            return;
        }

        defineImageViews();

        if (permissionManager.allPermissionsGranted()) {
            startCapturing();
            startStreaming();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    PermissionManager.REQUIRED_PERMISSIONS,
                    PermissionManager.REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startStreaming() {
        // Optional: stream frames over network
        cameraStream = new CameraStream(this, leftImage, rightImage);
        cameraStream.startStreaming(this);
    }

    private void startCapturing() {
        cameraController.startFrameCapture(frame -> {
            // Show input stream immediately
            runOnUiThread(() -> leftImage.setImageBitmap(frame));

            // Avoid overlapping inference calls
            if (isProcessing.get())
                return;
            isProcessing.set(true);

            inferExec.execute(() -> {
                try {
                    float[][][] detections = poseDetector.run(frame);
                    Bitmap output = poseDetector.drawSkeleton(frame, detections);

                    runOnUiThread(() -> {
                        rightImage.setImageBitmap(output);
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

        float shift = getResources().getDisplayMetrics().density * 25;
        leftImage.setTranslationX(shift);
        rightImage.setTranslationX(-shift);
    }

    private void hideSystemUI() {
        if (getSupportActionBar() != null)
            getSupportActionBar().hide();
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(getWindow(),
                getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionManager.REQUEST_CODE_PERMISSIONS) {
            if (permissionManager.allPermissionsGranted()) {
                startCapturing();
                startStreaming();
            } else {
                finish();
            }
        }
    }
}
