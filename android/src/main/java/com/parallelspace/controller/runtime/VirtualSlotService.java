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
    nativeHandle = nativeRuntime.createRuntime(getFilesDir().getAbsolutePath());
  }

  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    String instanceId = intent == null ? null : intent.getStringExtra("instance_id");
    String packageName = intent == null ? null : intent.getStringExtra("package_name");
    if (instanceId == null || packageName == null) return START_NOT_STICKY;
    String storagePath = new java.io.File(new java.io.File(getFilesDir(), "instances"), instanceId).getAbsolutePath();
    if (!nativeRuntime.mountInstanceStorage(nativeHandle, instanceId, storagePath)) return START_NOT_STICKY;
    // This initializes only the native compatibility slot; it does not load an APK.
    nativeRuntime.startInstance(nativeHandle, instanceId, packageName);
    return START_NOT_STICKY;
  }

  @Override public void onDestroy() {
    if (nativeHandle != 0L) nativeRuntime.destroyRuntime(nativeHandle);
    nativeHandle = 0L;
    super.onDestroy();
  }
}
