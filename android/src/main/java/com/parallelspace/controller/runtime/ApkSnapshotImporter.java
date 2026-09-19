package com.parallelverse.controller.runtime;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ActivityInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Build;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Imports a stable APK snapshot from an installed, launcher-visible package. */
public final class ApkSnapshotImporter {
  private static final int BUFFER_SIZE = 64 * 1024;

  private final PackageManager packageManager;
  private final File instancesRoot;

  public ApkSnapshotImporter(Context context, File instancesRoot) {
    this.packageManager = context.getPackageManager();
    this.instancesRoot = instancesRoot;
  }

  public VirtualPackageEntity importPackage(String instanceId, String packageName)
      throws PackageManager.NameNotFoundException, IOException {
    int metadataFlags = PackageManager.GET_ACTIVITIES
        | PackageManager.GET_SERVICES
        | PackageManager.GET_RECEIVERS
        | PackageManager.GET_PROVIDERS;
    int signingFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ? PackageManager.GET_SIGNING_CERTIFICATES
        : PackageManager.GET_SIGNATURES;
    PackageInfo packageInfo = packageManager.getPackageInfo(packageName, signingFlag | metadataFlags);
    ApplicationInfo applicationInfo = packageInfo.applicationInfo;
    if (applicationInfo == null || applicationInfo.sourceDir == null) {
      throw new IOException("The installed package does not expose a base APK.");
    }

    String launcherActivity = resolveLauncher(packageName);
    if (launcherActivity == null) {
      throw new IOException("The installed package does not expose a launcher activity.");
    }

    File packageRoot = new File(new File(instancesRoot, instanceId), "package");
    if (!packageRoot.mkdirs() && !packageRoot.isDirectory()) {
      throw new IOException("Cannot create the instance package directory.");
    }

    copyAtomically(new File(applicationInfo.sourceDir), new File(packageRoot, "base.apk"));
    List<String> splitPaths = new ArrayList<>();
    String[] installedSplits = applicationInfo.splitSourceDirs;
    if (installedSplits != null) {
      for (int index = 0; index < installedSplits.length; index++) {
        String relativePath = String.format(java.util.Locale.ROOT, "split-%03d.apk", index);
        copyAtomically(new File(installedSplits[index]), new File(packageRoot, relativePath));
        splitPaths.add("package/" + relativePath);
      }
    }

    return new VirtualPackageEntity(
        instanceId,
        packageName,
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? packageInfo.getLongVersionCode()
            : packageInfo.versionCode,
        packageInfo.versionName,
        launcherActivity,
        signerDigest(packageInfo),
        "package/base.apk",
        String.join("\n", splitPaths),
        componentNames(packageInfo.activities),
        componentNames(packageInfo.services),
        componentNames(packageInfo.receivers),
        componentNames(packageInfo.providers),
        System.currentTimeMillis());
  }

  private String resolveLauncher(String packageName) {
    Intent query = new Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(packageName);
    ResolveInfo result = packageManager.resolveActivity(query, 0);
    if (result == null || result.activityInfo == null) return null;
    return implementationClassName(result.activityInfo);
  }

  static PackageInfo verifyPackageFile(
      PackageManager packageManager,
      File apk,
      String expectedPackageName,
      String expectedSignerDigest) throws IOException {
    int signingFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        ? PackageManager.GET_SIGNING_CERTIFICATES
        : PackageManager.GET_SIGNATURES;
    PackageInfo packageInfo = packageManager.getPackageArchiveInfo(
        apk.getAbsolutePath(), signingFlag | PackageManager.GET_ACTIVITIES);
    if (packageInfo == null || !expectedPackageName.equals(packageInfo.packageName)) {
      throw new IOException("The imported APK package identity changed");
    }
    if (!expectedSignerDigest.equals(signerDigest(packageInfo))) {
      throw new IOException("The imported APK signer changed");
    }
    return packageInfo;
  }

  /** Resolves old records that persisted an activity-alias instead of its implementation class. */
  static String resolveRecordedLauncher(PackageInfo packageInfo, String recordedLauncher)
      throws IOException {
    String normalizedRecorded = normalizeComponentClassName(
        packageInfo.packageName, recordedLauncher);
    if (packageInfo.activities != null) {
      for (ActivityInfo activity : packageInfo.activities) {
        if (activity == null || activity.name == null) continue;
        String declaredName = normalizeComponentClassName(packageInfo.packageName, activity.name);
        String implementationName = implementationClassName(activity);
        if (normalizedRecorded.equals(declaredName)
            || normalizedRecorded.equals(implementationName)) {
          return implementationName;
        }
      }
    }
    throw new IOException("The recorded launcher component is not declared by the imported APK");
  }

  static String normalizeComponentClassName(String packageName, String className) {
    if (className == null || className.isBlank()) {
      throw new IllegalArgumentException("Component class name is missing");
    }
    if (className.charAt(0) == '.') return packageName + className;
    return className.indexOf('.') < 0 ? packageName + "." + className : className;
  }

  static String resolveActivityImplementationClassName(
      String packageName, String activityName, String targetActivity) {
    String implementationName = targetActivity == null || targetActivity.isBlank()
        ? activityName
        : targetActivity;
    return normalizeComponentClassName(packageName, implementationName);
  }

  private static String implementationClassName(ActivityInfo activity) {
    return resolveActivityImplementationClassName(
        activity.packageName, activity.name, activity.targetActivity);
  }

  static String signerDigest(PackageInfo packageInfo) throws IOException {
    Signature[] signatures;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      if (packageInfo.signingInfo == null) {
        throw new IOException("Package signing information is unavailable.");
      }
      signatures = packageInfo.signingInfo.hasMultipleSigners()
          ? packageInfo.signingInfo.getApkContentsSigners()
          : packageInfo.signingInfo.getSigningCertificateHistory();
    } else {
      signatures = packageInfo.signatures;
    }
    if (signatures == null || signatures.length == 0) {
      throw new IOException("The installed package has no signing certificates.");
    }
    List<String> digests = new ArrayList<>();
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      for (Signature signature : signatures) {
        digests.add(toHex(sha256.digest(signature.toByteArray())));
      }
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable.", impossible);
    }
    Collections.sort(digests);
    return String.join(":", digests);
  }

  private static String componentNames(ComponentInfo[] components) {
    if (components == null || components.length == 0) return "";
    List<String> names = new ArrayList<>();
    for (ComponentInfo component : components) {
      if (component != null && component.name != null) names.add(component.name);
    }
    Collections.sort(names);
    return String.join("\n", names);
  }

  private static void copyAtomically(File source, File destination) throws IOException {
    if (!source.isFile()) throw new IOException("An installed APK file is unavailable.");
    File temporary = new File(destination.getParentFile(), destination.getName() + ".partial");
    if (temporary.exists() && !temporary.delete()) {
      throw new IOException("Cannot remove an incomplete APK snapshot.");
    }
    try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
         FileOutputStream fileOutput = new FileOutputStream(temporary);
         BufferedOutputStream output = new BufferedOutputStream(fileOutput)) {
      // Android 14+ rejects writable dynamically loaded code. The already-open descriptor
      // remains writable while the path itself is sealed against replacement or mutation.
      InstanceStoragePaths.makeReadOnly(temporary);
      byte[] buffer = new byte[BUFFER_SIZE];
      int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    } catch (IOException error) {
      if (temporary.exists() && !temporary.delete()) temporary.deleteOnExit();
      throw error;
    }
    if (destination.exists() && !destination.delete()) {
      throw new IOException("Cannot replace the previous APK snapshot.");
    }
    if (!temporary.renameTo(destination)) {
      throw new IOException("Cannot finalize the APK snapshot.");
    }
  }

  private static String toHex(byte[] value) {
    StringBuilder result = new StringBuilder(value.length * 2);
    for (byte item : value) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 0xff));
    return result.toString();
  }
}
