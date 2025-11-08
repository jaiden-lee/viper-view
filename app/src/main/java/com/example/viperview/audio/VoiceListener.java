package com.example.viperview.audio;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

public class VoiceListener {

    private static final String TAG = "VoiceListener";
    private final SpeechRecognizer recognizer;
    private final Intent recognizerIntent;
    private boolean listeningForCommand = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VoiceCallback callback;
    private final Context context;

    public interface VoiceCallback {
        void onCommandDetected(String command);
        void onWakeWordDetected();
    }

    public VoiceListener(Context context, VoiceCallback callback) {
        this.context = context;
        this.callback = callback;

        recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        setupListener();
    }

    private void setupListener() {
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                Log.w(TAG, "SpeechRecognizer error: " + error);
                restartListening();
            }

            @Override
            public void onResults(Bundle results) {
                handleResults(results);
                restartListening();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                handlePartialResults(partialResults);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void handlePartialResults(Bundle partialResults) {
        ArrayList<String> matches = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        String text = matches.get(0).toLowerCase();

        if (!listeningForCommand && text.contains("viper")) {
            listeningForCommand = true;
            Log.d(TAG, "Wake word detected: " + text);
            callback.onWakeWordDetected();
        }
    }

    private void handleResults(Bundle results) {
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        String text = matches.get(0).toLowerCase();

        if (listeningForCommand) {
//            String command = text.replace("viper", "").trim();
//            String command = text;
            String command = text.substring(text.indexOf("viper") + "viper".length()).trim();
            Log.d(TAG, "Command: " + command);
            callback.onCommandDetected(command);
            listeningForCommand = false;
        }
    }

    public void startListening() {
        Log.d(TAG, "Starting to listen...");
        recognizer.startListening(recognizerIntent);
    }

    private void restartListening() {
        handler.postDelayed(() -> {
            Log.d(TAG, "Restarting listener...");
            recognizer.cancel();
            recognizer.startListening(recognizerIntent);
        }, 500);
    }

    public void stopListening() {
        recognizer.stopListening();
    }

    public void destroy() {
        recognizer.destroy();
    }
}
