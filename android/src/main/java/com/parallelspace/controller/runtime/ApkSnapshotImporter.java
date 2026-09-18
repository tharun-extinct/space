package com.parallelverse.controller.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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

    ComponentName launcher = resolveLauncher(packageName);
    if (launcher == null) {
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
        launcher.getClassName(),
        signerDigest(packageInfo),
        "package/base.apk",
        String.join("\n", splitPaths),
        componentNames(packageInfo.activities),
        componentNames(packageInfo.services),
        componentNames(packageInfo.receivers),
        componentNames(packageInfo.providers),
        System.currentTimeMillis());
  }

  private ComponentName resolveLauncher(String packageName) {
    Intent query = new Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setPackage(packageName);
    ResolveInfo result = packageManager.resolveActivity(query, 0);
    if (result == null || result.activityInfo == null) return null;
    return new ComponentName(result.activityInfo.packageName, result.activityInfo.name);
  }

  private static String signerDigest(PackageInfo packageInfo) throws IOException {
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
    try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(source));
         BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
      byte[] buffer = new byte[BUFFER_SIZE];
      int count;
      while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
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
