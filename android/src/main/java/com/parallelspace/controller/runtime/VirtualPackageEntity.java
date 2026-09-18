package com.parallelverse.controller.runtime;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Immutable metadata for the APK snapshot owned by one Parallel Verse instance. */
@Entity(tableName = "virtual_packages")
public final class VirtualPackageEntity {
  @PrimaryKey @NonNull public final String instanceId;
  @NonNull public final String packageName;
  public final long versionCode;
  public final String versionName;
  @NonNull public final String launcherActivity;
  @NonNull public final String signerSha256;
  @NonNull public final String baseApkRelativePath;
  @NonNull public final String splitApkRelativePaths;
  @NonNull public final String activityNames;
  @NonNull public final String serviceNames;
  @NonNull public final String receiverNames;
  @NonNull public final String providerNames;
  public final long importedAtEpochMs;

  public VirtualPackageEntity(
      String instanceId,
      String packageName,
      long versionCode,
      String versionName,
      String launcherActivity,
      String signerSha256,
      String baseApkRelativePath,
      String splitApkRelativePaths,
      String activityNames,
      String serviceNames,
      String receiverNames,
      String providerNames,
      long importedAtEpochMs) {
    this.instanceId = instanceId;
    this.packageName = packageName;
    this.versionCode = versionCode;
    this.versionName = versionName;
    this.launcherActivity = launcherActivity;
    this.signerSha256 = signerSha256;
    this.baseApkRelativePath = baseApkRelativePath;
    this.splitApkRelativePaths = splitApkRelativePaths;
    this.activityNames = activityNames;
    this.serviceNames = serviceNames;
    this.receiverNames = receiverNames;
    this.providerNames = providerNames;
    this.importedAtEpochMs = importedAtEpochMs;
  }
}
