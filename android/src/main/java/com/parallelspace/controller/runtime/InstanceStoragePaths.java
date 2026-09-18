package com.parallelverse.controller.runtime;

import java.io.File;
import java.io.IOException;

/** Canonical containment checks shared by package import consumers. */
public final class InstanceStoragePaths {
  private InstanceStoragePaths() {}

  public static File requireOwnedFile(File instanceRoot, String relativePath) throws IOException {
    if (relativePath == null || relativePath.isBlank() || new File(relativePath).isAbsolute()) {
      throw new IllegalArgumentException("An imported APK path must be relative");
    }
    return requireContainedFile(instanceRoot, new File(instanceRoot, relativePath));
  }

  public static File requireContainedFile(File instanceRoot, File candidate) throws IOException {
    File canonicalRoot = instanceRoot.getCanonicalFile();
    File canonicalCandidate = candidate.getCanonicalFile();
    if (canonicalCandidate.equals(canonicalRoot)
        || !canonicalCandidate.toPath().startsWith(canonicalRoot.toPath())) {
      throw new IllegalArgumentException("An imported APK path escaped its instance directory");
    }
    if (!canonicalCandidate.isFile()) {
      throw new IllegalArgumentException("An imported APK file is missing");
    }
    return canonicalCandidate;
  }

  /**
   * Resolves an instance-owned code file and removes write permission before Android loads it.
   * This also upgrades snapshots imported by older Parallel Verse builds.
   */
  public static File requireReadOnlyCodeFile(File instanceRoot, File candidate) throws IOException {
    File containedFile = requireContainedFile(instanceRoot, candidate);
    makeReadOnly(containedFile);
    return containedFile;
  }

  static void makeReadOnly(File file) throws IOException {
    if (file.canWrite() && !file.setReadOnly()) {
      throw new IOException("Cannot make the imported APK read-only");
    }
    if (file.canWrite()) {
      throw new IOException("The imported APK remains writable");
    }
  }
}
