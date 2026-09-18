package com.parallelverse.controller.runtime;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import androidx.room.Room;

/** Runtime owner in :runtime; this process deliberately has no Flutter dependency. */
public final class RuntimeService extends Service {
  static final String ACTION_SLOT_READY = "com.parallelverse.controller.runtime.SLOT_READY";
  static final String ACTION_SLOT_FAILED = "com.parallelverse.controller.runtime.SLOT_FAILED";
  static final String EXTRA_INSTANCE_ID = "instance_id";
  static final String EXTRA_LAUNCH_TOKEN = "launch_token";
  private RuntimeDatabase database;
  private RuntimeRepository repository;
  private final PendingLaunchRegistry pendingLaunches = new PendingLaunchRegistry();
  private java.util.concurrent.ExecutorService maintenanceExecutor;
  private final java.util.concurrent.CountDownLatch recoveryComplete =
      new java.util.concurrent.CountDownLatch(1);

  @Override public void onCreate() {
    super.onCreate();
    database = Room.databaseBuilder(getApplicationContext(), RuntimeDatabase.class, "runtime.db")
        .addMigrations(RuntimeDatabase.MIGRATION_1_2)
        .build();
    repository = new RuntimeRepository(getApplicationContext(), database);
    maintenanceExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    // Runtime slots cannot be trusted after their coordinator was reclaimed.
    stopService(new Intent(this, VirtualSlot0Service.class));
    stopService(new Intent(this, VirtualSlot1Service.class));
    maintenanceExecutor.execute(() -> {
      try {
        repository.reconcileAfterRuntimeRestart();
      } finally {
        recoveryComplete.countDown();
      }
    });
  }

  private final IRuntimeService.Stub binder = new IRuntimeService.Stub() {
    @Override public String createInstance(String packageName, String displayName) {
      awaitRecovery();
      return repository.create(packageName, displayName).id;
    }
    @Override public java.util.List<Bundle> listInstances() {
      awaitRecovery();
      java.util.List<Bundle> result = new java.util.ArrayList<>();
      for (InstanceEntity instance : repository.all()) {
        Bundle item = new Bundle();
        item.putString("id", instance.id);
        item.putString("packageName", instance.packageName);
        item.putString("displayName", instance.displayName);
        item.putString("state", instance.state);
        result.add(item);
      }
      return result;
    }
    @Override public Bundle startInstance(String instanceId) {
      awaitRecovery();
      VirtualPackageEntity virtualPackage = repository.requireVirtualPackage(instanceId);
      java.io.File baseApk = repository.resolveSnapshotFile(
          instanceId, virtualPackage.baseApkRelativePath);
      java.util.List<String> apkPaths = new java.util.ArrayList<>();
      apkPaths.add(baseApk.getAbsolutePath());
      if (!virtualPackage.splitApkRelativePaths.isBlank()) {
        for (String splitPath : virtualPackage.splitApkRelativePaths.split("\\n")) {
          apkPaths.add(repository.resolveSnapshotFile(instanceId, splitPath).getAbsolutePath());
        }
      }
      InstanceEntity instance = repository.reserveStart(instanceId);
      PendingLaunchRegistry.Ticket launch = pendingLaunches.issue(
          instance.id,
          instance.slot,
          instance.packageName,
          String.join(java.io.File.pathSeparator, apkPaths),
          virtualPackage.launcherActivity);
      Class<?> slotService = instance.slot == 0 ? VirtualSlot0Service.class : VirtualSlot1Service.class;
      Intent intent = new Intent(RuntimeService.this, slotService)
          .putExtra("instance_id", instance.id)
          .putExtra("package_name", instance.packageName)
          .putExtra("base_apk_path", baseApk.getAbsolutePath())
          .putExtra("apk_class_path", String.join(java.io.File.pathSeparator, apkPaths))
          .putExtra("launcher_activity", virtualPackage.launcherActivity)
          .putExtra(EXTRA_LAUNCH_TOKEN, launch.token)
          .putExtra("signer_sha256", virtualPackage.signerSha256);
      ComponentName started;
      try {
        started = startService(intent);
      } catch (RuntimeException error) {
        pendingLaunches.revoke(launch.token);
        repository.markStartFailed(instanceId);
        throw error;
      }
      if (started == null) {
        pendingLaunches.revoke(launch.token);
        repository.markStartFailed(instanceId);
        throw new IllegalStateException("Could not start runtime slot");
      }
      Bundle result = new Bundle();
      result.putString("launchToken", launch.token);
      result.putInt("slot", instance.slot);
      return result;
    }
    @Override public Bundle claimLaunch(String launchToken, int slot) {
      awaitRecovery();
      PendingLaunchRegistry.Claim claim = pendingLaunches.claim(launchToken, slot);
      Bundle result = new Bundle();
      result.putString("status", claim.status);
      if (claim.ticket != null) {
        result.putString("instanceId", claim.ticket.instanceId);
        result.putString("packageName", claim.ticket.packageName);
        result.putString("apkClassPath", claim.ticket.apkClassPath);
        result.putString("launcherActivity", claim.ticket.launcherActivity);
        result.putInt("slot", claim.ticket.slot);
      }
      return result;
    }
    @Override public void stopInstance(String instanceId) {
      awaitRecovery();
      InstanceEntity instance = repository.require(instanceId);
      pendingLaunches.revokeInstance(instanceId);
      if (instance.slot != null) {
        stopService(new Intent(RuntimeService.this, instance.slot == 0 ? VirtualSlot0Service.class : VirtualSlot1Service.class));
      }
      repository.markStopped(instanceId);
    }
    @Override public String getInstanceState(String instanceId) {
      awaitRecovery();
      InstanceEntity instance = database.instances().find(instanceId);
      return instance == null ? InstanceState.ERROR.name() : instance.state;
    }
  };

  private void awaitRecovery() {
    try {
      recoveryComplete.await();
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Runtime recovery was interrupted", error);
    }
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
    String instanceId = intent.getStringExtra(EXTRA_INSTANCE_ID);
    String launchToken = intent.getStringExtra(EXTRA_LAUNCH_TOKEN);
    if (instanceId == null) return START_NOT_STICKY;
    if (ACTION_SLOT_READY.equals(intent.getAction())) {
      maintenanceExecutor.execute(() -> {
        if (launchToken == null) {
          if (InstanceState.STARTING.name().equals(repository.require(instanceId).state)) {
            repository.markStartFailed(instanceId);
          }
          return;
        }
        repository.markRunning(instanceId);
        // Publish the capability only after durable controller state says the slot is running.
        if (!pendingLaunches.markReady(launchToken, instanceId)) {
          Integer failedSlot = repository.require(instanceId).slot;
          repository.markStopped(instanceId);
          if (failedSlot != null) {
            stopService(new Intent(
                RuntimeService.this,
                failedSlot == 0 ? VirtualSlot0Service.class : VirtualSlot1Service.class));
          }
        }
      });
    } else if (ACTION_SLOT_FAILED.equals(intent.getAction())) {
      maintenanceExecutor.execute(() -> {
        if (launchToken != null) pendingLaunches.revoke(launchToken);
        if (InstanceState.STARTING.name().equals(repository.require(instanceId).state)) {
          repository.markStartFailed(instanceId);
        }
      });
    }
    return START_NOT_STICKY;
  }

  @Override public IBinder onBind(Intent intent) { return binder; }

  @Override public void onDestroy() {
    maintenanceExecutor.shutdown();
    database.close();
    super.onDestroy();
  }
}
