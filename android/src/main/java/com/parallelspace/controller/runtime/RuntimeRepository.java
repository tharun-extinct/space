package com.parallelspace.controller.runtime;

import android.content.Context;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Java-owned durable source of truth for instance metadata and slot allocation. */
public final class RuntimeRepository {
  private static final int SLOT_COUNT = 2;
  private final RuntimeDatabase database;
  private final File instancesRoot;
  private final SlotAllocator slots = new SlotAllocator(SLOT_COUNT);

  public RuntimeRepository(Context context, RuntimeDatabase database) {
    this.database = database;
    this.instancesRoot = new File(context.getFilesDir(), "instances");
  }

  public synchronized InstanceEntity create(String packageName, String displayName, boolean supported) {
    String id = UUID.randomUUID().toString();
    InstanceState state = supported ? InstanceState.READY : InstanceState.UNSUPPORTED;
    File storage = new File(instancesRoot, id);
    if (!storage.mkdirs() && !storage.isDirectory()) throw new IllegalStateException("Cannot create instance storage");
    InstanceEntity instance = new InstanceEntity(id, packageName, displayName, state.name(), null, System.currentTimeMillis());
    database.instances().save(instance);
    return instance;
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

  public synchronized InstanceEntity require(String id) {
    InstanceEntity instance = database.instances().find(id);
    if (instance == null) throw new IllegalArgumentException("Unknown instance");
    return instance;
  }

  public synchronized List<InstanceEntity> all() { return database.instances().all(); }
}
