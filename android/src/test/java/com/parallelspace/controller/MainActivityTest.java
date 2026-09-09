package com.parallelspace.controller;

import static org.junit.Assert.assertTrue;

import io.flutter.embedding.android.FlutterActivity;
import org.junit.Test;

public final class MainActivityTest {
  @Test
  public void launcherHostsFlutter() {
    assertTrue(FlutterActivity.class.isAssignableFrom(MainActivity.class));
  }
}
