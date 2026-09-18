package com.parallelverse.controller.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** One bounded process slot. It must remain Android/Java-only. */
public class VirtualSlotService extends Service {
  private NativeRuntime nativeRuntime;
  private long nativeHandle;

  @Override public void onCreate() {
    super.onCreate();
  }

  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String instanceId = intent == null ? null : intent.getStringExtra("instance_id");
    String packageName = intent == null ? null : intent.getStringExtra("package_name");
    String baseApkPath = intent == null ? null : intent.getStringExtra("base_apk_path");
    String apkClassPath = intent == null ? null : intent.getStringExtra("apk_class_path");
    String launcherActivity = intent == null ? null : intent.getStringExtra("launcher_activity");
    String launchToken = intent == null ? null : intent.getStringExtra("launch_token");
    if (instanceId == null || packageName == null || baseApkPath == null || apkClassPath == null
        || launcherActivity == null || launchToken == null) return START_NOT_STICKY;
    String storagePath = new java.io.File(new java.io.File(getFilesDir(), "instances"), instanceId).getAbsolutePath();
    try {
      ensureNativeRuntime();
      InstanceStoragePaths.requireContainedFile(
          new java.io.File(storagePath), new java.io.File(baseApkPath));
      for (String apkPath : apkClassPath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
        InstanceStoragePaths.requireContainedFile(
            new java.io.File(storagePath), new java.io.File(apkPath));
      }
      GuestCodeLoader.loadLauncherWithoutInitialization(
          this, instanceId, apkClassPath, launcherActivity);
      if (!nativeRuntime.mountInstanceStorage(nativeHandle, instanceId, storagePath)) {
        throw new IllegalStateException("The native runtime rejected instance storage");
      }
      // Class resolution above does not initialize the guest or attach an Android component.
      nativeRuntime.startInstance(nativeHandle, instanceId, packageName);
      reportSlotState(RuntimeService.ACTION_SLOT_READY, instanceId, launchToken);
    } catch (java.io.IOException | ClassNotFoundException | LinkageError | RuntimeException error) {
      reportSlotState(RuntimeService.ACTION_SLOT_FAILED, instanceId, launchToken,
          safeFailureMessage(error));
      stopSelf(startId);
    }
    return START_NOT_STICKY;
  }

  private void ensureNativeRuntime() {
    if (nativeRuntime != null && nativeHandle != 0L) return;
    nativeRuntime = new NativeRuntime();
    java.io.File runtimeRoot = new java.io.File(getFilesDir(), "instances");
    nativeHandle = nativeRuntime.createRuntime(runtimeRoot.getAbsolutePath());
    if (nativeHandle == 0L) {
      throw new IllegalStateException("The native runtime could not be created");
    }
  }

  private static String safeFailureMessage(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
    return message.length() > 240 ? message.substring(0, 240) : message;
  }

  private void reportSlotState(String action, String instanceId, String launchToken) {
    reportSlotState(action, instanceId, launchToken, null);
  }

  private void reportSlotState(
      String action, String instanceId, String launchToken, String failureMessage) {
    Intent report = new Intent(this, RuntimeService.class)
        .setAction(action)
        .putExtra(RuntimeService.EXTRA_INSTANCE_ID, instanceId)
        .putExtra(RuntimeService.EXTRA_LAUNCH_TOKEN, launchToken);
    if (failureMessage != null) {
      report.putExtra(RuntimeService.EXTRA_FAILURE_MESSAGE, failureMessage);
    }
    try {
      startService(report);
    } catch (RuntimeException ignored) {
      // Never crash the slot while it is already reporting a controlled failure.
      // The visible stub timeout and coordinator restart recovery clear STARTING.
    }
  }

  @Override public void onDestroy() {
    if (nativeHandle != 0L && nativeRuntime != null) {
      try {
        nativeRuntime.destroyRuntime(nativeHandle);
      } catch (RuntimeException | LinkageError ignored) {
        // The slot is already terminating; controller recovery owns durable state.
      }
    }
    nativeHandle = 0L;
    super.onDestroy();
  }
}
