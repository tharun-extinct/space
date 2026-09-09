package com.parallelverse.controller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import com.parallelverse.controller.runtime.IRuntimeService;
import com.parallelverse.controller.runtime.RuntimeService;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Host for Flutter in production. This is the sole process allowed to initialize it. */
public final class MainActivity extends FlutterActivity {
  private static final String CHANNEL = "com.parallelverse.controller/runtime";

  private final List<Runnable> pendingRuntimeCalls = new ArrayList<>();
  private IRuntimeService runtimeService;
  private boolean runtimeBinding;
  private boolean runtimeBound;

  private final ServiceConnection runtimeConnection = new ServiceConnection() {
    @Override public void onServiceConnected(ComponentName name, IBinder service) {
      runtimeService = IRuntimeService.Stub.asInterface(service);
      runtimeBinding = false;
      runtimeBound = true;
      List<Runnable> calls = new ArrayList<>(pendingRuntimeCalls);
      pendingRuntimeCalls.clear();
      for (Runnable call : calls) call.run();
    }

    @Override public void onServiceDisconnected(ComponentName name) {
      runtimeService = null;
    }
  };

  @Override public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
    super.configureFlutterEngine(flutterEngine);
    new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
        .setMethodCallHandler((call, result) -> {
          switch (call.method) {
            case "listInstalledApps":
              result.success(listInstalledApps());
              break;
            case "listInstances":
              withRuntime(result, () -> result.success(listInstances()));
              break;
            case "createInstance":
              String packageName = call.argument("packageName");
              String displayName = call.argument("displayName");
              if (packageName == null || displayName == null) {
                result.error("invalid_arguments", "App details are missing.", null);
                return;
              }
              withRuntime(result, () -> result.success(createInstance(packageName, displayName)));
              break;
            case "openInstance":
              String instanceId = call.argument("instanceId");
              if (instanceId == null || instanceId.isBlank()) {
                result.error("invalid_arguments", "The app instance is missing.", null);
                return;
              }
              withRuntime(result, () -> {
                openInstance(instanceId);
                result.success(null);
              });
              break;
            default:
              result.notImplemented();
          }
        });
  }

  private List<Map<String, Object>> listInstalledApps() {
    PackageManager manager = getPackageManager();
    Intent launcherIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> launchers = manager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL);
    Map<String, Map<String, Object>> uniqueApps = new HashMap<>();
    for (ResolveInfo info : launchers) {
      String packageName = info.activityInfo.packageName;
      if (getPackageName().equals(packageName)) continue;
      Map<String, Object> app = new HashMap<>();
      app.put("packageName", packageName);
      app.put("displayName", info.loadLabel(manager).toString());
      uniqueApps.put(packageName, app);
    }
    List<Map<String, Object>> apps = new ArrayList<>(uniqueApps.values());
    apps.sort(Comparator.comparing(item -> (String) item.get("displayName"), String.CASE_INSENSITIVE_ORDER));
    return apps;
  }

  private List<Map<String, Object>> listInstances() throws Exception {
    List<Map<String, Object>> instances = new ArrayList<>();
    for (Bundle item : runtimeService.listInstances()) {
      instances.add(bundleToMap(item));
    }
    return instances;
  }

  private Map<String, Object> createInstance(String packageName, String displayName) throws Exception {
    String id = runtimeService.createInstance(packageName, displayName);
    for (Bundle item : runtimeService.listInstances()) {
      if (id.equals(item.getString("id"))) return bundleToMap(item);
    }
    throw new IllegalStateException("The instance was created but could not be loaded.");
  }

  /**
   * Opens the source application's launcher as an explicit interim capability.
   * The compatibility runtime does not yet load APK components, so this must not
   * be represented as isolated cloned execution.
   */
  private void openInstance(String instanceId) throws Exception {
    Bundle instance = null;
    for (Bundle item : runtimeService.listInstances()) {
      if (instanceId.equals(item.getString("id"))) {
        instance = item;
        break;
      }
    }
    if (instance == null) throw new IllegalArgumentException("This app instance no longer exists.");
    if (!"READY".equals(instance.getString("state"))
        && !"STOPPED".equals(instance.getString("state"))) {
      throw new IllegalStateException("This app instance is not ready to open.");
    }

    String packageName = instance.getString("packageName");
    Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
    if (launchIntent == null) {
      throw new IllegalStateException("The selected app is no longer installed or has no launcher activity.");
    }
    launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    startActivity(launchIntent);
  }

  private Map<String, Object> bundleToMap(Bundle item) {
    Map<String, Object> instance = new HashMap<>();
    instance.put("id", item.getString("id", ""));
    instance.put("packageName", item.getString("packageName", ""));
    instance.put("displayName", item.getString("displayName", ""));
    instance.put("state", item.getString("state", "ERROR"));
    return instance;
  }

  private void withRuntime(MethodChannel.Result result, ThrowingRunnable operation) {
    Runnable call = () -> {
      try {
        operation.run();
      } catch (Exception error) {
        result.error("runtime_error", error.getMessage(), null);
      }
    };
    if (runtimeService != null) {
      call.run();
      return;
    }
    pendingRuntimeCalls.add(call);
    if (runtimeBinding) return;
    runtimeBinding = true;
    boolean started = bindService(
        new Intent(this, RuntimeService.class), runtimeConnection, Context.BIND_AUTO_CREATE);
    runtimeBound = started;
    if (!started) {
      runtimeBinding = false;
      pendingRuntimeCalls.remove(call);
      result.error("runtime_unavailable", "Could not connect to the runtime service.", null);
    }
  }

  @Override protected void onDestroy() {
    if (runtimeBound) unbindService(runtimeConnection);
    runtimeBound = false;
    runtimeService = null;
    super.onDestroy();
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
