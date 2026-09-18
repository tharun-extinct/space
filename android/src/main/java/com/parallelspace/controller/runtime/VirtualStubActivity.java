package com.parallelverse.controller.runtime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Manifest-declared Activity shell for one virtual process slot.
 *
 * <p>This establishes component routing and a slot-owned window. It deliberately does not claim
 * that an arbitrary guest Activity can be attached yet.</p>
 */
public abstract class VirtualStubActivity extends Activity {
  public static final String EXTRA_LAUNCH_TOKEN = "launch_token";
  public static final String EXTRA_SLOT = "slot";
  private static final long POLL_INTERVAL_MS = 100L;
  private static final int MAX_POLLS = 150;

  private final Handler handler = new Handler(Looper.getMainLooper());
  private IRuntimeService runtimeService;
  private boolean bound;
  private int attempts;
  private String launchToken;
  private String instanceId;
  private int slot;
  private TextView statusView;
  private ProgressBar progress;

  private final ServiceConnection connection = new ServiceConnection() {
    @Override public void onServiceConnected(ComponentName name, IBinder service) {
      runtimeService = IRuntimeService.Stub.asInterface(service);
      pollLaunch();
    }

    @Override public void onServiceDisconnected(ComponentName name) {
      runtimeService = null;
      showFailure("The runtime coordinator disconnected.");
    }
  };

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    launchToken = getIntent().getStringExtra(EXTRA_LAUNCH_TOKEN);
    slot = getIntent().getIntExtra(EXTRA_SLOT, -1);
    buildPreparingView();
    if (launchToken == null || slot != expectedSlot()) {
      showFailure("This virtual launch request is invalid.");
      return;
    }
    bound = bindService(
        new Intent(this, RuntimeService.class), connection, Context.BIND_AUTO_CREATE);
    if (!bound) showFailure("Could not connect to the runtime coordinator.");
  }

  protected abstract int expectedSlot();

  private void pollLaunch() {
    if (runtimeService == null || isFinishing()) return;
    try {
      Bundle claim = runtimeService.claimLaunch(launchToken, slot);
      instanceId = claim.getString("instanceId", instanceId);
      String status = claim.getString("status", PendingLaunchRegistry.STATUS_INVALID);
      if (PendingLaunchRegistry.STATUS_READY.equals(status)) {
        showPrepared(claim);
        return;
      }
      if (PendingLaunchRegistry.STATUS_PENDING.equals(status) && attempts++ < MAX_POLLS) {
        handler.postDelayed(this::pollLaunch, POLL_INTERVAL_MS);
        return;
      }
      showFailure(PendingLaunchRegistry.STATUS_PENDING.equals(status)
          ? "The virtual process did not become ready in time."
          : "This virtual launch request expired or was already used.");
    } catch (Exception error) {
      showFailure(error.getMessage() == null ? "Could not prepare the virtual launch." : error.getMessage());
    }
  }

  private void buildPreparingView() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER);
    int padding = Math.round(32 * getResources().getDisplayMetrics().density);
    layout.setPadding(padding, padding, padding, padding);
    progress = new ProgressBar(this);
    layout.addView(progress);
    statusView = new TextView(this);
    statusView.setGravity(Gravity.CENTER);
    statusView.setText("Preparing isolated runtime…");
    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    textParams.topMargin = padding / 2;
    layout.addView(statusView, textParams);
    setContentView(layout);
  }

  private void showPrepared(Bundle claim) {
    progress.setVisibility(ProgressBar.GONE);
    statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    statusView.setText(
        "Runtime slot prepared for " + claim.getString("packageName", "the selected app")
            + ".\n\nGuest Activity attachment is the next engine milestone; "
            + "the device-installed app was not opened.");
  }

  private void showFailure(String message) {
    if (progress != null) progress.setVisibility(ProgressBar.GONE);
    if (statusView != null) statusView.setText(message);
  }

  @Override protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    if (runtimeService != null && instanceId != null) {
      try {
        runtimeService.stopInstance(instanceId);
      } catch (Exception ignored) {
        // Runtime recovery will clear the process-local slot if this process or Binder is gone.
      }
    }
    if (bound) unbindService(connection);
    bound = false;
    runtimeService = null;
    super.onDestroy();
  }
}
