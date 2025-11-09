package com.example.viperview.camera_stream;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.media.Image;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraStream {

    public interface FrameProcessor {
        Bitmap process(Bitmap frame);
    }

    private FrameProcessor frameProcessor;

    private static final String TAG = "CameraStream";
    private static final int CONNECT_TIMEOUT_MS = 4000;

    private Socket socket;
    private OutputStream output;
    private InputStream input;
    private final ImageView leftImage;
    private final ImageView rightImage;
    private final Context context;

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    private final String serverIp = "100.118.244.118";
    // private final String serverIp = "192.168.1.2";
    // private final String serverIp = "100.101.13.39";
    private final int serverPort = 9999;

    private volatile boolean isProcessingFrame = false;

    private volatile boolean mlEnabled = false; // toggle ML on/off

    // Allow external toggling
    public void toggleMLEnabled() {
        this.mlEnabled = !this.mlEnabled;
    }

    public CameraStream(Context context, ImageView leftImage, ImageView rightImage) {
        this.context = context;
        this.leftImage = leftImage;
        this.rightImage = rightImage;
    }

    @OptIn(markerClass = ExperimentalCamera2Interop.class)
    public void startStreaming(LifecycleOwner lifecycleOwner) {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Find ultrawide camera
                String ultraWideId = findUltraWideCameraId();
                CameraSelector cameraSelector;

                if (ultraWideId != null) {
                    Log.d(TAG, "Found ultrawide camera: " + ultraWideId);
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
                    Log.d(TAG, "Ultrawide not found, using default back camera");
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                }

                // Hidden preview to keep camera alive
                Preview preview = new Preview.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .build();
                preview.setSurfaceProvider(null);

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                analysis.setAnalyzer(analysisExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        analysis);

                Log.d(TAG, "Camera streaming started.");
                ensureConnected();
            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    public void setFrameProcessor(FrameProcessor processor) {
        this.frameProcessor = processor;
    }

    private String findUltraWideCameraId() {
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null)
            return null;

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
                        android.util.SizeF sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);

                        if (sensorSize != null) {
                            float sensorWidth = sensorSize.getWidth();
                            float fov = 2 * (float) Math.atan(sensorWidth / (2 * focal));

                            Log.d(TAG, "Camera " + id + " FOV: " + Math.toDegrees(fov) + "°");

                            if (fov > maxFov) {
                                maxFov = fov;
                                bestId = id;
                            }
                        }
                    }
                }
            }

            if (bestId != null) {
                Log.d(TAG, "Selected camera with widest FOV: " + Math.toDegrees(maxFov) + "°");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding ultrawide camera", e);
        }

        return bestId;
    }

    private void analyzeFrame(ImageProxy image) {
        try {
            byte[] nv21 = imageProxyToNV21(image);
            if (nv21 != null) {
                networkExecutor.execute(() -> sendRawFrame(nv21));
            }
        } catch (Exception e) {
            Log.e(TAG, "Analyzer error", e);
        } finally {
            image.close();
        }
    }

    private byte[] imageProxyToNV21(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] nv21 = new byte[width * height * 3 / 2];

        // Copy Y plane
        ByteBuffer yBuffer = planes[0].getBuffer();
        int yRowStride = planes[0].getRowStride();
        for (int row = 0; row < height; row++) {
            yBuffer.position(row * yRowStride);
            yBuffer.get(nv21, row * width, width);
        }

        // Copy VU interleaved plane
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int uvRowStride = planes[1].getRowStride();
        int uvPixelStride = planes[1].getPixelStride();

        int offset = width * height;
        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                nv21[offset++] = vBuffer.get(row * uvRowStride + col * uvPixelStride);
                nv21[offset++] = uBuffer.get(row * uvRowStride + col * uvPixelStride);
            }
        }
        return nv21;
    }

    private void sendRawFrame(byte[] nv21) {
        try {
            if (output == null)
                return;
            ByteBuffer header = ByteBuffer.allocate(5);
            header.putInt(nv21.length);
            header.put((byte) (mlEnabled ? 1 : 0));
            output.write(header.array());
            output.write(nv21);
            output.flush();
        } catch (Exception e) {
            Log.e(TAG, "sendRawFrame failed", e);
            closeSocket();
        }
    }

    private void receiveFrames() {
        new Thread(() -> {
            try {
                if (input == null)
                    return;
                while (true) {
                    byte[] lenBytes = new byte[4];
                    if (input.read(lenBytes) != 4)
                        break;
                    int frameLen = ByteBuffer.wrap(lenBytes).getInt();
                    byte[] jpegBytes = input.readNBytes(frameLen);

                    // Skip frame if UI is still processing
                    if (isProcessingFrame) {
                        Log.d(TAG, "Dropping frame - UI busy");
                        continue;
                    }

                    isProcessingFrame = true;
                    Bitmap bmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
                    if (bmp != null) {
                        Bitmap processedBmp = (frameProcessor != null) ? frameProcessor.process(bmp) : bmp;

                        leftImage.post(() -> {
                            leftImage.setImageBitmap(processedBmp);
                            rightImage.setImageBitmap(processedBmp);
                            isProcessingFrame = false;
                        });
                    } else {
                        isProcessingFrame = false;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "receiveFrames error", e);
                closeSocket();
            }
        }).start();
    }

    private void ensureConnected() {
        networkExecutor.execute(() -> {
            try {
                if (socket != null && socket.isConnected() && !socket.isClosed())
                    return;

                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, serverPort), CONNECT_TIMEOUT_MS);
                output = socket.getOutputStream();
                input = socket.getInputStream();

                Log.d(TAG, "Connected to server");

                receiveFrames();
            } catch (Exception e) {
                Log.e(TAG, "Failed to connect", e);
                closeSocket();
            }
        });
    }

    private void closeSocket() {
        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
        socket = null;
        output = null;
        input = null;
    }
}