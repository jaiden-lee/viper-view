package com.example.viperview;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.example.viperview.audio.VoiceListener;

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
    private VoiceListener voiceListener;
    private boolean displaySkeletons = true;
    private boolean displayBBox = true;

    private float zoomFactor = 1.0f;
    private float targetZoom = 1.0f;
    private final float MAX_ZOOM = 3.0f;
    private final float MIN_ZOOM = 1.0f;
    private ValueAnimator zoomAnimator;

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
            // startCapturing();
            startStreaming();
            setupVoiceListener();
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

    private void setupVoiceListener() {
        voiceListener = new VoiceListener(this, new VoiceListener.VoiceCallback() {
            @Override
            public void onWakeWordDetected() {
                // runOnUiThread(() ->
                //// android.widget.Toast.makeText(MainActivity.this, "Viper detected!",
                // android.widget.Toast.LENGTH_SHORT).show()
                // );
            }

            @Override
            public void onCommandDetected(String command) {
                runOnUiThread(() -> handleVoiceCommand(command));
            }
        });

        voiceListener.startListening();
    }

    private void handleVoiceCommand(String command) {
        if (command.contains("highlight")) {
            // android.widget.Toast.makeText(this, "Activating thermal highlight!",
            // android.widget.Toast.LENGTH_SHORT).show();
            // You could trigger your YOLO or thermal highlight logic here
        } else if (command.contains("skeleton")) {
            displaySkeletons = !displaySkeletons;
            // android.widget.Toast.makeText(this, "Toggling skeletons",
            // android.widget.Toast.LENGTH_SHORT).show();
        } else if (command.contains("box")) {
            displayBBox = !displayBBox;
        } else if (command.contains("zoom in")) {
            targetZoom = MAX_ZOOM;
            animateZoomChange();
        } else if (command.contains("zoom out")) {
            targetZoom = MIN_ZOOM;
            animateZoomChange();
        } else {
            // android.widget.Toast.makeText(this, "Command: " + command,
            // android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void animateZoomChange() {
        if (zoomAnimator != null && zoomAnimator.isRunning()) {
            zoomAnimator.cancel();
        }

        zoomAnimator = ValueAnimator.ofFloat(zoomFactor, targetZoom);
        zoomAnimator.setDuration(300); // milliseconds
        zoomAnimator.setInterpolator(new DecelerateInterpolator()); // smooth ease-out

        zoomAnimator.addUpdateListener(anim -> {
            zoomFactor = (float) anim.getAnimatedValue();
            // The next frame rendered by your camera loop will automatically use this
            // zoomFactor
        });

        zoomAnimator.start();
    }

    private Bitmap applyZoom(Bitmap frame, float zoomFactor) {
        if (zoomFactor <= 1.01f)
            return frame; // no zoom

        int width = frame.getWidth();
        int height = frame.getHeight();

        // Calculate cropped region
        int cropWidth = (int) (width / zoomFactor);
        int cropHeight = (int) (height / zoomFactor);

        int xOffset = (width - cropWidth) / 2;
        int yOffset = (height - cropHeight) / 2;

        Bitmap cropped = Bitmap.createBitmap(frame, xOffset, yOffset, cropWidth, cropHeight);
        // Scale cropped region back to original size for display
        return Bitmap.createScaledBitmap(cropped, width, height, true);
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
                Bitmap zoomedFrame = applyZoom(frame, zoomFactor);

                try {
                    if (displaySkeletons || displayBBox) {
                        float[][][] detections = poseDetector.run(zoomedFrame);
                        Bitmap result = poseDetector.drawSkeleton(zoomedFrame, detections, displaySkeletons,
                                displayBBox);

                        runOnUiThread(() -> {
                            leftImage.setImageBitmap(result);
                            rightImage.setImageBitmap(result);
                        });
                    } else {
                        runOnUiThread(() -> {
                            leftImage.setImageBitmap(zoomedFrame);
                            rightImage.setImageBitmap(zoomedFrame);
                        });
                    }

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
                // startCapturing();
                startStreaming();
                setupVoiceListener();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceListener != null)
            voiceListener.destroy();
        inferExec.shutdown();
    }

}
