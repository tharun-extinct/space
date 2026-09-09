package com.parallelverse.controller.runtime;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import androidx.room.Room;

/** Runtime owner in :runtime; this process deliberately has no Flutter dependency. */
public final class RuntimeService extends Service {
  private RuntimeDatabase database;
  private RuntimeRepository repository;

  @Override public void onCreate() {
    super.onCreate();
    database = Room.databaseBuilder(getApplicationContext(), RuntimeDatabase.class, "runtime.db").build();
    repository = new RuntimeRepository(getApplicationContext(), database);
    // Runtime slots cannot be trusted after their coordinator was reclaimed.
    stopService(new Intent(this, VirtualSlot0Service.class));
    stopService(new Intent(this, VirtualSlot1Service.class));
    repository.reconcileAfterRuntimeRestart();
  }

  private final IRuntimeService.Stub binder = new IRuntimeService.Stub() {
    @Override public String createInstance(String packageName, String displayName) {
      return repository.create(packageName, displayName, isInstalledPackage(packageName)).id;
    }
    @Override public void startInstance(String instanceId) {
      InstanceEntity instance = repository.reserveStart(instanceId);
      Class<?> slotService = instance.slot == 0 ? VirtualSlot0Service.class : VirtualSlot1Service.class;
      Intent intent = new Intent(RuntimeService.this, slotService)
          .putExtra("instance_id", instance.id)
          .putExtra("package_name", instance.packageName);
      ComponentName started = startService(intent);
      if (started == null) throw new IllegalStateException("Could not start runtime slot");
      database.instances().transition(instance.id, InstanceState.RUNNING.name(), instance.slot, System.currentTimeMillis(),
          java.util.Collections.singletonList(InstanceState.STARTING.name()));
    }
    @Override public void stopInstance(String instanceId) {
      InstanceEntity instance = repository.require(instanceId);
      if (instance.slot != null) {
        stopService(new Intent(RuntimeService.this, instance.slot == 0 ? VirtualSlot0Service.class : VirtualSlot1Service.class));
      }
      repository.markStopped(instanceId);
    }
    @Override public String getInstanceState(String instanceId) {
      InstanceEntity instance = database.instances().find(instanceId);
      return instance == null ? InstanceState.ERROR.name() : instance.state;
    }
  };

  private boolean isInstalledPackage(String packageName) {
    try { getPackageManager().getApplicationInfo(packageName, 0); return true; }
    catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return false; }
  }
  @Override public IBinder onBind(Intent intent) { return binder; }
}
