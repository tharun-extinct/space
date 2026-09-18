package com.parallelverse.controller.runtime;

import android.app.Activity;
import android.content.Context;
import dalvik.system.DexClassLoader;
import java.io.File;

/** Prepares guest DEX without initializing guest classes or Android components. */
public final class GuestCodeLoader {
  private GuestCodeLoader() {}

  public static Class<?> loadLauncherWithoutInitialization(
      Context context, String instanceId, String apkClassPath, String launcherActivity)
      throws ClassNotFoundException {
    File optimizedDirectory = new File(context.getCodeCacheDir(), "instances/" + instanceId);
    if (!optimizedDirectory.mkdirs() && !optimizedDirectory.isDirectory()) {
      throw new IllegalStateException("Cannot create the guest code-cache directory");
    }
    DexClassLoader classLoader = new DexClassLoader(
        apkClassPath,
        optimizedDirectory.getAbsolutePath(),
        null,
        context.getClassLoader());
    Class<?> launcherClass = Class.forName(launcherActivity, false, classLoader);
    if (!Activity.class.isAssignableFrom(launcherClass)) {
      throw new IllegalStateException("The recorded launcher is not an Android Activity");
    }
    return launcherClass;
  }
}
