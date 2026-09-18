package com.parallelverse.controller.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class InstanceStoragePathsTest {
  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test public void resolvesAFileInsideTheInstance() throws Exception {
    File root = temporaryFolder.newFolder("instance");
    File packageDirectory = new File(root, "package");
    if (!packageDirectory.mkdirs()) throw new IllegalStateException("Could not create test directory");
    File apk = new File(packageDirectory, "base.apk");
    if (!apk.createNewFile()) throw new IllegalStateException("Could not create test APK");

    assertEquals(apk.getCanonicalFile(),
        InstanceStoragePaths.requireOwnedFile(root, "package/base.apk"));
  }

  @Test public void rejectsParentTraversal() throws Exception {
    File parent = temporaryFolder.newFolder("parent");
    File root = new File(parent, "instance");
    if (!root.mkdirs()) throw new IllegalStateException("Could not create test directory");
    File outside = new File(parent, "outside.apk");
    if (!outside.createNewFile()) throw new IllegalStateException("Could not create test APK");

    assertThrows(IllegalArgumentException.class,
        () -> InstanceStoragePaths.requireOwnedFile(root, "../outside.apk"));
  }

  @Test public void sealsAnExistingSnapshotBeforeDynamicLoading() throws Exception {
    File root = temporaryFolder.newFolder("legacy-instance");
    File packageDirectory = new File(root, "package");
    if (!packageDirectory.mkdirs()) throw new IllegalStateException("Could not create test directory");
    File apk = new File(packageDirectory, "base.apk");
    if (!apk.createNewFile()) throw new IllegalStateException("Could not create test APK");

    assertEquals(apk.getCanonicalFile(),
        InstanceStoragePaths.requireReadOnlyCodeFile(root, apk));
    assertFalse(apk.canWrite());
  }
}
