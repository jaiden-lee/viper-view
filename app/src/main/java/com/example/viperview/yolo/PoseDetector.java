package com.example.viperview.yolo;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.nnapi.NnApiDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class PoseDetector {
    private Interpreter interpreter;

    public PoseDetector(AssetManager assetManager, String modelPath) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        options.addDelegate(new NnApiDelegate());
        interpreter = new Interpreter(loadModelFile(assetManager, modelPath), options);
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public float[][][] run(Bitmap bitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[224 * 224];
        resized.getPixels(pixels, 0, 224, 0, 0, 224, 224);
        for (int p : pixels) {
            // RGB normalized [0,1]
            inputBuffer.putFloat(((p >> 16) & 0xFF) / 255f);
            inputBuffer.putFloat(((p >> 8) & 0xFF) / 255f);
            inputBuffer.putFloat((p & 0xFF) / 255f);
        }
        inputBuffer.rewind(); // ✅ important

        // ✅ query actual output shape
        int[] outShape = interpreter.getOutputTensor(0).shape(); // e.g. [1, 8400, 56]
        float[][][] output = new float[outShape[0]][outShape[1]][outShape[2]];
        interpreter.run(inputBuffer, output);
        return output;
    }

    public Bitmap drawSkeleton(Bitmap frame, float[][][] detections) {
        Bitmap mutable = frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutable);
        Paint paint = new Paint();
        paint.setStrokeWidth(3f);
        paint.setStyle(Paint.Style.FILL);

        int width = frame.getWidth();
        int height = frame.getHeight();

        // Step 1: Collect all detections above confidence threshold
        List<float[]> allDetections = new ArrayList<>();
        for (int i = 0; i < detections[0][0].length; i++) {
            float conf = detections[0][4][i];
            if (conf < 0.3f)
                continue; // skip weak detections

            float[] det = new float[56];
            for (int j = 0; j < 56; j++)
                det[j] = detections[0][j][i];
            allDetections.add(det);
        }

        // Step 2: Apply NMS to merge overlapping detections
        List<float[]> filtered = nonMaxSuppression(allDetections, 0.45f);

        // Step 3: Define COCO keypoint connection pairs
        int[][] skeletonPairs = {
                { 5, 6 }, // shoulders
                { 5, 7 }, { 7, 9 }, // left arm
                { 6, 8 }, { 8, 10 }, // right arm
                { 5, 11 }, { 6, 12 }, // torso sides
                { 11, 12 }, // hips
                { 11, 13 }, { 13, 15 }, // left leg
                { 12, 14 }, { 14, 16 }, // right leg
                { 0, 1 }, { 0, 2 }, { 1, 3 }, { 2, 4 } // face connections
        };

        // Step 4: Draw all filtered detections
        for (float[] det : filtered) {
            // random color per person
            paint.setColor(Color.rgb(
                    (int) (0),
                    (int) (255),
                    (int) (0)));

            // extract bbox
            float cx = det[0], cy = det[1], w = det[2], h = det[3];
            float left = (cx - w / 2) * width;
            float top = (cy - h / 2) * height;
            float right = (cx + w / 2) * width;
            float bottom = (cy + h / 2) * height;

            // draw bounding box
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRect(left, top, right, bottom, paint);

            // draw keypoints
            paint.setStyle(Paint.Style.FILL);
            for (int k = 0; k < 17; k++) {
                float x = det[5 + k * 3] * width;
                float y = det[5 + k * 3 + 1] * height;
                float c = det[5 + k * 3 + 2];
                if (c > 0.3f) {
                    canvas.drawCircle(x, y, 4, paint);
                }
            }

            // connect keypoints
            paint.setStrokeWidth(2f);
            for (int[] pair : skeletonPairs) {
                int i1 = pair[0];
                int i2 = pair[1];
                float x1 = det[5 + i1 * 3] * width;
                float y1 = det[5 + i1 * 3 + 1] * height;
                float x2 = det[5 + i2 * 3] * width;
                float y2 = det[5 + i2 * 3 + 1] * height;
                float c1 = det[5 + i1 * 3 + 2];
                float c2 = det[5 + i2 * 3 + 2];
                if (c1 > 0.3f && c2 > 0.3f)
                    canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }

        return mutable;
    }

    private float iou(float[] a, float[] b) {
        float ax1 = a[0] - a[2] / 2, ay1 = a[1] - a[3] / 2;
        float ax2 = a[0] + a[2] / 2, ay2 = a[1] + a[3] / 2;
        float bx1 = b[0] - b[2] / 2, by1 = b[1] - b[3] / 2;
        float bx2 = b[0] + b[2] / 2, by2 = b[1] + b[3] / 2;

        float interX1 = Math.max(ax1, bx1);
        float interY1 = Math.max(ay1, by1);
        float interX2 = Math.min(ax2, bx2);
        float interY2 = Math.min(ay2, by2);
        float interArea = Math.max(0, interX2 - interX1) * Math.max(0, interY2 - interY1);

        float areaA = (ax2 - ax1) * (ay2 - ay1);
        float areaB = (bx2 - bx1) * (by2 - by1);

        return interArea / (areaA + areaB - interArea + 1e-6f);
    }

    private List<float[]> nonMaxSuppression(List<float[]> detections, float iouThreshold) {
        List<float[]> results = new ArrayList<>();
        detections.sort((a, b) -> Float.compare(b[4], a[4])); // sort by confidence

        boolean[] removed = new boolean[detections.size()];
        for (int i = 0; i < detections.size(); i++) {
            if (removed[i])
                continue;
            results.add(detections.get(i));
            for (int j = i + 1; j < detections.size(); j++) {
                if (removed[j])
                    continue;
                if (iou(detections.get(i), detections.get(j)) > iouThreshold) {
                    removed[j] = true;
                }
            }
        }
        return results;
    }

    public void close() {
        interpreter.close();
    }
}
