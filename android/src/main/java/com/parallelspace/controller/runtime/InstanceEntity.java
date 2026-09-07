package com.parallelspace.controller.runtime;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "instances")
public final class InstanceEntity {
  @PrimaryKey @NonNull public final String id;
  public final String packageName;
  public final String displayName;
  public final String state;
  public final Integer slot;
  public final long updatedAtEpochMs;

  public InstanceEntity(String id, String packageName, String displayName, String state, Integer slot, long updatedAtEpochMs) {
    this.id = id; this.packageName = packageName; this.displayName = displayName;
    this.state = state; this.slot = slot; this.updatedAtEpochMs = updatedAtEpochMs;
  }
}
