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
    nativeRuntime = new NativeRuntime();
    java.io.File runtimeRoot = new java.io.File(getFilesDir(), "instances");
    nativeHandle = nativeRuntime.createRuntime(runtimeRoot.getAbsolutePath());
  }

  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String instanceId = intent == null ? null : intent.getStringExtra("instance_id");
    String packageName = intent == null ? null : intent.getStringExtra("package_name");
    String baseApkPath = intent == null ? null : intent.getStringExtra("base_apk_path");
    String apkClassPath = intent == null ? null : intent.getStringExtra("apk_class_path");
    String launcherActivity = intent == null ? null : intent.getStringExtra("launcher_activity");
    if (instanceId == null || packageName == null || baseApkPath == null || apkClassPath == null
        || launcherActivity == null) return START_NOT_STICKY;
    String storagePath = new java.io.File(new java.io.File(getFilesDir(), "instances"), instanceId).getAbsolutePath();
    try {
      InstanceStoragePaths.requireContainedFile(
          new java.io.File(storagePath), new java.io.File(baseApkPath));
      for (String apkPath : apkClassPath.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
        InstanceStoragePaths.requireContainedFile(
            new java.io.File(storagePath), new java.io.File(apkPath));
      }
      GuestCodeLoader.loadLauncherWithoutInitialization(
          this, instanceId, apkClassPath, launcherActivity);
      if (!nativeRuntime.mountInstanceStorage(nativeHandle, instanceId, storagePath)) {
        stopSelf(startId);
        return START_NOT_STICKY;
      }
      // Class resolution above does not initialize the guest or attach an Android component.
      nativeRuntime.startInstance(nativeHandle, instanceId, packageName);
      reportSlotState(RuntimeService.ACTION_SLOT_READY, instanceId);
    } catch (java.io.IOException | ClassNotFoundException | IllegalArgumentException
        | IllegalStateException error) {
      reportSlotState(RuntimeService.ACTION_SLOT_FAILED, instanceId);
      stopSelf(startId);
    }
    return START_NOT_STICKY;
  }

  private void reportSlotState(String action, String instanceId) {
    startService(new Intent(this, RuntimeService.class)
        .setAction(action)
        .putExtra(RuntimeService.EXTRA_INSTANCE_ID, instanceId));
  }

  @Override public void onDestroy() {
    if (nativeHandle != 0L) nativeRuntime.destroyRuntime(nativeHandle);
    nativeHandle = 0L;
    super.onDestroy();
  }
}
