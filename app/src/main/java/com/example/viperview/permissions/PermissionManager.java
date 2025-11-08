package com.example.viperview.permissions;

import android.app.Activity;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionManager {
    private final Activity activity;

    public static final int REQUEST_CODE_PERMISSIONS = 10;
    public static final String[] REQUIRED_PERMISSIONS = new String[] {
            android.Manifest.permission.CAMERA
    };

    public PermissionManager(Activity activity) {
        this.activity = activity;
    }

    public boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void requestPermissions() {
        ActivityCompat.requestPermissions(activity, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }
}
