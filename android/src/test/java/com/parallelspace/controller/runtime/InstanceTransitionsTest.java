package com.parallelspace.controller.runtime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class InstanceTransitionsTest {
  @Test public void permitsAnIdempotentRetry() {
    assertTrue(InstanceTransitions.allows(InstanceState.READY, InstanceState.READY));
  }
  @Test public void permitsNormalStartPath() {
    assertTrue(InstanceTransitions.allows(InstanceState.READY, InstanceState.STARTING));
    assertTrue(InstanceTransitions.allows(InstanceState.STARTING, InstanceState.RUNNING));
  }
  @Test public void deniesRunningBeforeReadiness() {
    assertFalse(InstanceTransitions.allows(InstanceState.DRAFT, InstanceState.RUNNING));
  }
}
