package com.parallelverse.controller.runtime;

/** Coarse, versioned JNI boundary. Android framework routing stays in Java. */
public final class NativeRuntime {
  public static final int API_VERSION = 1;
  private static boolean libraryLoaded;

  public NativeRuntime() {
    ensureLibraryLoaded();
    int nativeVersion = getApiVersion();
    if (nativeVersion != API_VERSION) {
      throw new UnsupportedOperationException(
          "Native runtime API mismatch: Java=" + API_VERSION + ", native=" + nativeVersion);
    }
  }

  private static synchronized void ensureLibraryLoaded() {
    if (libraryLoaded) return;
    try {
      System.loadLibrary("parallel_runtime");
      libraryLoaded = true;
    } catch (LinkageError error) {
      throw new IllegalStateException(
          "The native runtime is unavailable on this device", error);
    }
  }

  private static native int getApiVersion();

  public native long createRuntime(String storagePath);

  public native boolean mountInstanceStorage(long runtime, String instanceId, String storagePath);

  public native void startInstance(long runtime, String instanceId, String packageDescriptor);

  public native void stopInstance(long runtime, String instanceId);

  public native String getRuntimeStatus(long runtime, String instanceId);

  public native void destroyRuntime(long runtime);
}
