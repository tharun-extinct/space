package com.parallelspace.controller.runtime;

/** Pure transition guard, shared by service and recovery code. */
public final class InstanceTransitions {
  private InstanceTransitions() {}

  public static boolean allows(InstanceState from, InstanceState to) {
    if (from == to) return true; // idempotent retries
    switch (from) {
      case DRAFT: return to == InstanceState.INSTALLING || to == InstanceState.UNSUPPORTED || to == InstanceState.ERROR;
      case INSTALLING: return to == InstanceState.READY || to == InstanceState.UNSUPPORTED || to == InstanceState.ERROR;
      case READY: return to == InstanceState.STARTING || to == InstanceState.STOPPED || to == InstanceState.ERROR;
      case STARTING: return to == InstanceState.RUNNING || to == InstanceState.ERROR || to == InstanceState.STOPPED;
      case RUNNING: return to == InstanceState.STOPPING || to == InstanceState.ERROR;
      case STOPPING: return to == InstanceState.STOPPED || to == InstanceState.ERROR;
      case STOPPED: return to == InstanceState.STARTING || to == InstanceState.ERROR;
      default: return false;
    }
  }
}
