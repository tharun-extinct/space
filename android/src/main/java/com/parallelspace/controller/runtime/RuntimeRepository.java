package com.parallelverse.controller.runtime;

import android.content.Context;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Java-owned durable source of truth for instance metadata and slot allocation. */
public final class RuntimeRepository {
  private static final int SLOT_COUNT = 2;
  private final RuntimeDatabase database;
  private final File instancesRoot;
  private final ApkSnapshotImporter apkImporter;
  private final SlotAllocator slots = new SlotAllocator(SLOT_COUNT);

  public RuntimeRepository(Context context, RuntimeDatabase database) {
    this.database = database;
    this.instancesRoot = new File(context.getFilesDir(), "instances");
    this.apkImporter = new ApkSnapshotImporter(context, instancesRoot);
  }

  public synchronized InstanceEntity create(String packageName, String displayName) {
    String id = UUID.randomUUID().toString();
    File storage = new File(instancesRoot, id);
    if (!storage.mkdirs() && !storage.isDirectory()) throw new IllegalStateException("Cannot create instance storage");
    InstanceEntity instance = new InstanceEntity(
        id, packageName, displayName, InstanceState.DRAFT.name(), null, System.currentTimeMillis());
    database.instances().save(instance);
    transition(id, InstanceState.INSTALLING, Arrays.asList(InstanceState.DRAFT));
    try {
      VirtualPackageEntity virtualPackage = apkImporter.importPackage(id, packageName);
      database.virtualPackages().save(virtualPackage);
      transition(id, InstanceState.READY, Arrays.asList(InstanceState.INSTALLING));
    } catch (PackageManager.NameNotFoundException | IOException error) {
      transition(id, InstanceState.UNSUPPORTED, Arrays.asList(InstanceState.INSTALLING));
    }
    return require(id);
  }

  private void transition(String id, InstanceState state, List<InstanceState> from) {
    List<String> allowed = new java.util.ArrayList<>();
    for (InstanceState item : from) allowed.add(item.name());
    int changed = database.instances().transition(
        id, state.name(), null, System.currentTimeMillis(), allowed);
    if (changed != 1) throw new IllegalStateException("Instance state changed unexpectedly");
  }

  public synchronized InstanceEntity reserveStart(String id) {
    InstanceEntity existing = require(id);
    Integer slot = slots.firstFree(database.instances().all());
    if (slot == null) throw new IllegalStateException("All runtime slots are occupied");
    int changed = database.instances().transition(id, InstanceState.STARTING.name(), slot, System.currentTimeMillis(),
        Arrays.asList(InstanceState.READY.name(), InstanceState.STOPPED.name()));
    if (changed != 1) throw new IllegalStateException("Instance is not ready to start");
    return require(id);
  }

  public synchronized void markStopped(String id) {
    database.instances().transition(id, InstanceState.STOPPED.name(), null, System.currentTimeMillis(),
        Arrays.asList(InstanceState.RUNNING.name(), InstanceState.STARTING.name(), InstanceState.STOPPING.name()));
  }

  /** A runtime-process restart cannot prove slot liveness, so it conservatively clears reservations. */
  public synchronized void reconcileAfterRuntimeRestart() {
    for (InstanceEntity instance : database.instances().all()) {
      if (InstanceState.STARTING.name().equals(instance.state) || InstanceState.RUNNING.name().equals(instance.state)
          || InstanceState.STOPPING.name().equals(instance.state)) {
        database.instances().transition(instance.id, InstanceState.STOPPED.name(), null, System.currentTimeMillis(),
            Arrays.asList(InstanceState.STARTING.name(), InstanceState.RUNNING.name(), InstanceState.STOPPING.name()));
      }
    }
  }

  public synchronized InstanceEntity require(String id) {
    InstanceEntity instance = database.instances().find(id);
    if (instance == null) throw new IllegalArgumentException("Unknown instance");
    return instance;
  }

  public synchronized VirtualPackageEntity requireVirtualPackage(String instanceId) {
    VirtualPackageEntity virtualPackage = database.virtualPackages().find(instanceId);
    if (virtualPackage == null) throw new IllegalStateException("The instance has no imported APK snapshot");
    return virtualPackage;
  }

  public synchronized File resolveSnapshotFile(String instanceId, String relativePath) {
    try {
      return InstanceStoragePaths.requireOwnedFile(
          new File(instancesRoot, instanceId), relativePath);
    } catch (IOException | IllegalArgumentException error) {
      throw new IllegalStateException("Could not resolve the imported APK snapshot", error);
    }
  }

  public synchronized List<InstanceEntity> all() { return database.instances().all(); }
}
