package com.example.viperview;

import android.os.Bundle;

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

public class MainActivity extends AppCompatActivity {

    private CameraController cameraController;
    private PermissionManager permissionManager;

    private PreviewView leftView;
    private PreviewView rightView;
    private Preview leftPreview;
    private Preview rightPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        hideSystemUI();

        cameraController = new CameraController(this);
        permissionManager = new PermissionManager(this);

        defineCameraViews();

        if (permissionManager.allPermissionsGranted()) {
            cameraController.startCamera(leftPreview, rightPreview, leftView, rightView);
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    PermissionManager.REQUIRED_PERMISSIONS,
                    PermissionManager.REQUEST_CODE_PERMISSIONS
            );
        }
    }


    private void defineCameraViews() {
        leftView = findViewById(R.id.leftView);
        rightView = findViewById(R.id.rightView);
        leftView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        rightView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        leftPreview = new Preview.Builder().build();
        rightPreview = new Preview.Builder().build();
        leftPreview.setSurfaceProvider(leftView.getSurfaceProvider());
        rightPreview.setSurfaceProvider(rightView.getSurfaceProvider());

        float shift = getResources().getDisplayMetrics().density * 25; // 20dp
        leftView.setTranslationX(shift);
        rightView.setTranslationX(-shift);
    }

    private void hideSystemUI() {
        // Hide the action bar if present
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Make content appear behind system bars
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        // Get the controller
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());

        // Hide both navigation and status bars
        insetsController.hide(WindowInsetsCompat.Type.systemBars());

        // Set behavior so bars reappear only with swipe gestures
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        );

        // Keep the screen on
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionManager.REQUEST_CODE_PERMISSIONS) {
            if (permissionManager.allPermissionsGranted()) {
                cameraController.startCamera(leftPreview, rightPreview, leftView, rightView);
            } else {
                finish(); // Exit if denied
            }
        }
    }
}
