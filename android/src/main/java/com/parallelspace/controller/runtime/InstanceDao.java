package com.parallelspace.controller.runtime;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface InstanceDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE) void save(InstanceEntity instance);
  @Query("SELECT * FROM instances WHERE id = :id") InstanceEntity find(String id);
  @Query("SELECT * FROM instances ORDER BY updatedAtEpochMs DESC") java.util.List<InstanceEntity> all();
  @Query("UPDATE instances SET state = :state, slot = :slot, updatedAtEpochMs = :updatedAt WHERE id = :id AND state IN (:allowedStates)")
  int transition(String id, String state, Integer slot, long updatedAt, java.util.List<String> allowedStates);
}
