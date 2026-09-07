package com.parallelspace.controller.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** One bounded process slot. It must remain Android/Java-only. */
public class VirtualSlotService extends Service {
  @Override public IBinder onBind(Intent intent) { return null; }
  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    // A later compatibility engine receives the already-assigned instance ID here.
    return START_NOT_STICKY;
  }
}
