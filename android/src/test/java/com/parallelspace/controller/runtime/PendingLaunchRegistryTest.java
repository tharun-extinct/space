package com.parallelverse.controller.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PendingLaunchRegistryTest {
  @Test
  public void claimIsPendingUntilTheMatchingSlotReportsReady() {
    MutableClock clock = new MutableClock();
    PendingLaunchRegistry registry = new PendingLaunchRegistry(clock, 1_000L);
    PendingLaunchRegistry.Ticket ticket = registry.issue(
        "instance-1", 1, "example.app", "snapshot/base.apk", "example.app.MainActivity");

    PendingLaunchRegistry.Claim pending = registry.claim(ticket.token, 1);
    assertEquals(PendingLaunchRegistry.STATUS_PENDING, pending.status);
    assertEquals("instance-1", pending.ticket.instanceId);
    assertFalse(registry.markReady(ticket.token, "another-instance"));
    assertTrue(registry.markReady(ticket.token, "instance-1"));

    PendingLaunchRegistry.Claim claim = registry.claim(ticket.token, 1);
    assertEquals(PendingLaunchRegistry.STATUS_READY, claim.status);
    assertEquals("instance-1", claim.ticket.instanceId);
    assertEquals(PendingLaunchRegistry.STATUS_INVALID, registry.claim(ticket.token, 1).status);
  }

  @Test
  public void wrongSlotCannotConsumeTicket() {
    PendingLaunchRegistry registry = new PendingLaunchRegistry(() -> 0L, 1_000L);
    PendingLaunchRegistry.Ticket ticket = registry.issue(
        "instance-1", 0, "example.app", "snapshot/base.apk", "example.app.MainActivity");
    assertTrue(registry.markReady(ticket.token, "instance-1"));

    PendingLaunchRegistry.Claim wrongSlot = registry.claim(ticket.token, 1);
    assertEquals(PendingLaunchRegistry.STATUS_INVALID, wrongSlot.status);
    assertNull(wrongSlot.ticket);
    assertEquals(PendingLaunchRegistry.STATUS_READY, registry.claim(ticket.token, 0).status);
  }

  @Test
  public void expiredTicketFailsClosed() {
    MutableClock clock = new MutableClock();
    PendingLaunchRegistry registry = new PendingLaunchRegistry(clock, 10L);
    PendingLaunchRegistry.Ticket ticket = registry.issue(
        "instance-1", 0, "example.app", "snapshot/base.apk", "example.app.MainActivity");
    clock.now = 10L;

    assertFalse(registry.markReady(ticket.token, "instance-1"));
    assertEquals(PendingLaunchRegistry.STATUS_INVALID, registry.claim(ticket.token, 0).status);
  }

  private static final class MutableClock implements PendingLaunchRegistry.Clock {
    long now;
    @Override public long nowMillis() { return now; }
  }
}
