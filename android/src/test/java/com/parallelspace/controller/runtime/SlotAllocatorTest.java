package com.parallelspace.controller.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import org.junit.Test;

public final class SlotAllocatorTest {
  private static InstanceEntity instance(String id, InstanceState state, Integer slot) {
    return new InstanceEntity(id, "example.package", id, state.name(), slot, 0L);
  }

  @Test public void selectsTheLowestAvailableSlot() {
    SlotAllocator allocator = new SlotAllocator(2);
    assertEquals(Integer.valueOf(1), allocator.firstFree(Arrays.asList(
        instance("one", InstanceState.RUNNING, 0))));
  }

  @Test public void doesNotCountStoppedInstancesAsOccupyingSlots() {
    SlotAllocator allocator = new SlotAllocator(2);
    assertEquals(Integer.valueOf(0), allocator.firstFree(Arrays.asList(
        instance("one", InstanceState.STOPPED, 0))));
  }

  @Test public void signalsCapacityExhaustion() {
    SlotAllocator allocator = new SlotAllocator(2);
    assertNull(allocator.firstFree(Arrays.asList(
        instance("one", InstanceState.RUNNING, 0),
        instance("two", InstanceState.STARTING, 1))));
  }
}
