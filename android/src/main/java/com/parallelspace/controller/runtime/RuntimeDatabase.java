package com.parallelverse.controller.runtime;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {InstanceEntity.class}, version = 1, exportSchema = true)
public abstract class RuntimeDatabase extends RoomDatabase {
  public abstract InstanceDao instances();
}
