package com.parallelverse.controller.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class ApkSnapshotImporterTest {
  @Test public void resolvesAliasToItsImplementationClass() {
    assertEquals(
        "com.microsoft.sapphire.app.main.MainActivity",
        ApkSnapshotImporter.resolveActivityImplementationClassName(
            "com.microsoft.bing",
            "com.microsoft.sapphire.app.main.LauncherAlias",
            "com.microsoft.sapphire.app.main.MainActivity"));
  }

  @Test public void expandsUnqualifiedComponentName() {
    assertEquals(
        "com.example.app.MainActivity",
        ApkSnapshotImporter.normalizeComponentClassName("com.example.app", "MainActivity"));
  }

  @Test public void preservesFullyQualifiedComponentName() {
    assertEquals(
        "com.microsoft.sapphire.app.main.LauncherAlias",
        ApkSnapshotImporter.normalizeComponentClassName(
            "com.microsoft.bing", "com.microsoft.sapphire.app.main.LauncherAlias"));
  }

  @Test public void rejectsMissingComponentName() {
    assertThrows(
        IllegalArgumentException.class,
        () -> ApkSnapshotImporter.normalizeComponentClassName("com.example.app", ""));
  }
}
