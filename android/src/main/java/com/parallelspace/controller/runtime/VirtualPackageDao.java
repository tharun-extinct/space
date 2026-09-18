package com.parallelverse.controller.runtime;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface VirtualPackageDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  void save(VirtualPackageEntity virtualPackage);

  @Query("SELECT * FROM virtual_packages WHERE instanceId = :instanceId")
  VirtualPackageEntity find(String instanceId);
}
