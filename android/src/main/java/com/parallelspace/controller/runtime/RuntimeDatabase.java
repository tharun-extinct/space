package com.parallelverse.controller.runtime;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {InstanceEntity.class, VirtualPackageEntity.class}, version = 2, exportSchema = true)
public abstract class RuntimeDatabase extends RoomDatabase {
  public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
    @Override public void migrate(SupportSQLiteDatabase database) {
      database.execSQL("CREATE TABLE IF NOT EXISTS `virtual_packages` "
          + "(`instanceId` TEXT NOT NULL, `packageName` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, "
          + "`versionName` TEXT, `launcherActivity` TEXT NOT NULL, `signerSha256` TEXT NOT NULL, "
          + "`baseApkRelativePath` TEXT NOT NULL, `splitApkRelativePaths` TEXT NOT NULL, "
          + "`activityNames` TEXT NOT NULL, `serviceNames` TEXT NOT NULL, "
          + "`receiverNames` TEXT NOT NULL, `providerNames` TEXT NOT NULL, "
          + "`importedAtEpochMs` INTEGER NOT NULL, PRIMARY KEY(`instanceId`))");
    }
  };

  public abstract InstanceDao instances();
  public abstract VirtualPackageDao virtualPackages();
}
