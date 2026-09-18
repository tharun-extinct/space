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
}
