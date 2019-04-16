package com.jordanalcaraz.audiorecorder.audiorecorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * AudioRecorderPlugin
 */
public class AudioRecorderPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {
  static final int REQUEST_RECORD_AUDIO_PERMISSION = 2366;

  private final Registrar registrar;
  private boolean isRecording = false;
  private static final String LOG_TAG = "AudioRecorder";
  private MediaRecorder mRecorder = null;
  private static String mFilePath = null;
  private Date startTime = null;
  private String mExtension = "";

  private MethodCall mMethodCall;
  private Result mPendingResult;
  /**
   * Plugin registration.
   */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "audio_recorder");

    final AudioRecorderPlugin audioRecorderPlugin = new AudioRecorderPlugin(registrar);
    channel.setMethodCallHandler(audioRecorderPlugin);
    registrar.addRequestPermissionsResultListener(audioRecorderPlugin);
  }

  private AudioRecorderPlugin(Registrar registrar){
    this.registrar = registrar;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    switch (call.method) {
      case "start":
        Log.d(LOG_TAG, "Call start");
        String path = call.argument("path");
        mExtension = call.argument("extension");
        startTime = Calendar.getInstance().getTime();
        if (path != null) {
          mFilePath = path;
        } else {
          String fileName = String.valueOf(startTime.getTime());
          mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + fileName + mExtension;
        }
        Log.d(LOG_TAG, mFilePath);
        startRecording();
        isRecording = true;
        result.success(null);
        break;
      case "stop":
        Log.d(LOG_TAG, "Call stop");
        stopRecording();
        long duration = Calendar.getInstance().getTime().getTime() - startTime.getTime();
        Log.d(LOG_TAG, "Duration: " + String.valueOf(duration));
        isRecording = false;
        HashMap<String, Object> recordingResult = new HashMap<>();
        recordingResult.put("duration", duration);
        recordingResult.put("path", mFilePath);
        recordingResult.put("audioOutputFormat", mExtension);
        result.success(recordingResult);
        break;
      case "isRecording":
        Log.d(LOG_TAG, "Call isRecording");
        result.success(isRecording);
        break;
      case "requestPermissions": {
        Log.d(LOG_TAG, "Call requestPermissions");

        if (registrar.activity() == null) {
          result.error("no_activity", "audio_recorder plugin requires a foreground activity.", null);
          break;
        }

        final boolean hasPermissions = this.hasPermissions(registrar.activity());
        if (hasPermissions) {
          result.success(true);
          break;
        }

        setPendingMethodCallAndResult(call, result);
        requestPermissions(registrar.activity());
        break;
      }
      case "hasPermissions": {
        Log.d(LOG_TAG, "Call hasPermissions");

        if (registrar.activity() == null) {
          result.error("no_activity", "audio_recorder plugin requires a foreground activity.", null);
          break;
        }

        final boolean hasPermissions = this.hasPermissions(registrar.activity());
        result.success(hasPermissions);
        break;
      }
      default:
        result.notImplemented();
        break;
    }
  }

  private boolean hasPermissions(final Activity activity) {
    return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED;
  }

  private void requestPermissions(final Activity activity) {
    ActivityCompat.requestPermissions(activity, new String[] {Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
  }

  private void startRecording() {
    mRecorder = new MediaRecorder();
    mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    mRecorder.setOutputFormat(getOutputFormatFromString(mExtension));
    mRecorder.setOutputFile(mFilePath);
    mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

    try {
      mRecorder.prepare();
    } catch (IOException e) {
      Log.e(LOG_TAG, "prepare() failed");
    }

    mRecorder.start();
  }

  private void stopRecording() {
    if (mRecorder != null){
      mRecorder.stop();
      mRecorder.reset();
      mRecorder.release();
      mRecorder = null;
    }
  }

  private int getOutputFormatFromString(String outputFormat) {
    switch (outputFormat) {
      case ".mp4":
      case ".aac":
      case ".m4a":
        return MediaRecorder.OutputFormat.MPEG_4;
      default:
        return MediaRecorder.OutputFormat.MPEG_4;
    }
  }

  private boolean setPendingMethodCallAndResult(
          MethodCall methodCall, MethodChannel.Result result) {
    if (mPendingResult != null) {
      return false;
    }

    mMethodCall = methodCall;
    mPendingResult = result;

    return true;
  }

  private void clearMethodCallAndResult() {
    mMethodCall = null;
    mPendingResult = null;
  }

  private void flushRequestPermissionsResult(boolean value) {
    if (mPendingResult == null || mMethodCall == null || !mMethodCall.method.equals("requestPermissions")) return;

    mPendingResult.success(value);

    clearMethodCallAndResult();
  }

  @Override
  public boolean onRequestPermissionsResult(
          int requestCode, String[] permissions, int[] grantResults) {
    boolean permissionGranted =
            grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    
    switch (requestCode) {
      case REQUEST_RECORD_AUDIO_PERMISSION:
        flushRequestPermissionsResult(permissionGranted);
        break;
        default:
          return false;
    }

    return true;
  }
}
