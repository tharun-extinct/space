package com.parallelverse.controller.runtime;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Process-local, one-time capabilities used to route a controller launch into a slot Activity. */
final class PendingLaunchRegistry {
  static final String STATUS_PENDING = "PENDING";
  static final String STATUS_READY = "READY";
  static final String STATUS_INVALID = "INVALID";
  private static final long DEFAULT_TTL_MS = 30_000L;

  interface Clock {
    long nowMillis();
  }

  static final class Ticket {
    final String token;
    final String instanceId;
    final int slot;
    final String packageName;
    final String apkClassPath;
    final String launcherActivity;
    final long expiresAtMillis;
    boolean ready;

    Ticket(
        String token,
        String instanceId,
        int slot,
        String packageName,
        String apkClassPath,
        String launcherActivity,
        long expiresAtMillis) {
      this.token = token;
      this.instanceId = instanceId;
      this.slot = slot;
      this.packageName = packageName;
      this.apkClassPath = apkClassPath;
      this.launcherActivity = launcherActivity;
      this.expiresAtMillis = expiresAtMillis;
    }
  }

  static final class Claim {
    final String status;
    final Ticket ticket;

    private Claim(String status, Ticket ticket) {
      this.status = status;
      this.ticket = ticket;
    }

    static Claim pending(Ticket ticket) { return new Claim(STATUS_PENDING, ticket); }
    static Claim ready(Ticket ticket) { return new Claim(STATUS_READY, ticket); }
    static Claim invalid() { return new Claim(STATUS_INVALID, null); }
  }

  private final Map<String, Ticket> tickets = new HashMap<>();
  private final Clock clock;
  private final long ttlMillis;

  PendingLaunchRegistry() {
    this(System::currentTimeMillis, DEFAULT_TTL_MS);
  }

  PendingLaunchRegistry(Clock clock, long ttlMillis) {
    this.clock = clock;
    this.ttlMillis = ttlMillis;
  }

  synchronized Ticket issue(
      String instanceId,
      int slot,
      String packageName,
      String apkClassPath,
      String launcherActivity) {
    purgeExpired();
    for (Iterator<Ticket> iterator = tickets.values().iterator(); iterator.hasNext(); ) {
      if (iterator.next().instanceId.equals(instanceId)) iterator.remove();
    }
    String token = UUID.randomUUID().toString();
    Ticket ticket = new Ticket(
        token,
        instanceId,
        slot,
        packageName,
        apkClassPath,
        launcherActivity,
        clock.nowMillis() + ttlMillis);
    tickets.put(token, ticket);
    return ticket;
  }

  synchronized boolean markReady(String token, String instanceId) {
    purgeExpired();
    Ticket ticket = tickets.get(token);
    if (ticket == null || !ticket.instanceId.equals(instanceId)) return false;
    ticket.ready = true;
    return true;
  }

  synchronized Claim claim(String token, int slot) {
    purgeExpired();
    Ticket ticket = tickets.get(token);
    if (ticket == null || ticket.slot != slot) return Claim.invalid();
    if (!ticket.ready) return Claim.pending(ticket);
    tickets.remove(token);
    return Claim.ready(ticket);
  }

  synchronized void revoke(String token) {
    tickets.remove(token);
  }

  synchronized void revokeInstance(String instanceId) {
    tickets.values().removeIf(ticket -> ticket.instanceId.equals(instanceId));
  }

  private void purgeExpired() {
    long now = clock.nowMillis();
    tickets.values().removeIf(ticket -> ticket.expiresAtMillis <= now);
  }
}
