package com.parallelspace.controller.runtime;

import java.util.HashSet;
import java.util.Set;

/** Deterministically selects a bounded process slot; persistence remains in RuntimeRepository. */
public final class SlotAllocator {
  private final int slotCount;

  public SlotAllocator(int slotCount) {
    if (slotCount < 1) throw new IllegalArgumentException("slotCount must be positive");
    this.slotCount = slotCount;
  }

  public Integer firstFree(Iterable<InstanceEntity> instances) {
    Set<Integer> occupied = new HashSet<>();
    for (InstanceEntity instance : instances) {
      if ((InstanceState.STARTING.name().equals(instance.state) || InstanceState.RUNNING.name().equals(instance.state))
          && instance.slot != null) occupied.add(instance.slot);
    }
    for (int slot = 0; slot < slotCount; slot++) if (!occupied.contains(slot)) return slot;
    return null;
  }
}
