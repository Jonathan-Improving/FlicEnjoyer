package com.flicenjoyer.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DataLoaderTest {

  @Test
  void loadDoesNotBlockCallingThread() throws Exception {
    var loader = new DataLoader<String>();
    var started = new CountDownLatch(1);
    var done = new CountDownLatch(1);

    long before = System.nanoTime();
    loader.load(
        () -> {
          started.countDown();
          try {
            Thread.sleep(200);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return "result";
        },
        result -> done.countDown(),
        ex -> done.countDown());
    long elapsed = (System.nanoTime() - before) / 1_000_000;

    // load() should return almost immediately (< 50ms), not wait 200ms
    assertTrue(elapsed < 50, "load() blocked for " + elapsed + "ms, expected < 50ms");
    assertTrue(started.await(2, TimeUnit.SECONDS), "task should have started");
    assertTrue(done.await(2, TimeUnit.SECONDS), "task should have completed");
  }

  @Test
  void loadDeliversResultOnCompletion() throws Exception {
    var loader = new DataLoader<Integer>();
    var result = new AtomicReference<Integer>();
    var done = new CountDownLatch(1);

    loader.load(
        () -> 42,
        val -> {
          result.set(val);
          done.countDown();
        },
        ex -> done.countDown());

    assertTrue(done.await(2, TimeUnit.SECONDS));
    assertEquals(42, result.get());
  }

  @Test
  void loadDeliversFailureOnException() throws Exception {
    var loader = new DataLoader<String>();
    var error = new AtomicReference<Exception>();
    var done = new CountDownLatch(1);

    loader.load(
        () -> {
          throw new RuntimeException("boom");
        },
        val -> done.countDown(),
        ex -> {
          error.set(ex);
          done.countDown();
        });

    assertTrue(done.await(2, TimeUnit.SECONDS));
    assertNotNull(error.get());
    assertEquals("boom", error.get().getMessage());
  }

  @Test
  void cancelPreventsResultDelivery() throws Exception {
    var loader = new DataLoader<String>();
    var resultDelivered = new AtomicBoolean(false);
    var taskStarted = new CountDownLatch(1);
    var taskCanProceed = new CountDownLatch(1);
    var done = new CountDownLatch(1);

    loader.load(
        () -> {
          taskStarted.countDown();
          try {
            taskCanProceed.await(2, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return "should not deliver";
        },
        val -> {
          resultDelivered.set(true);
          done.countDown();
        },
        ex -> done.countDown());

    assertTrue(taskStarted.await(2, TimeUnit.SECONDS));
    loader.cancel();
    taskCanProceed.countDown();

    // Give time for task to finish
    Thread.sleep(200);
    assertFalse(resultDelivered.get(), "cancelled task should not deliver result");
  }

  @Test
  void tasksAreSerializedNotConcurrent() throws Exception {
    var loader = new DataLoader<Integer>();
    var order = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
    var firstStarted = new CountDownLatch(1);
    var secondDone = new CountDownLatch(1);

    loader.load(
        () -> {
          firstStarted.countDown();
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          order.add(1);
          return 1;
        },
        val -> {},
        ex -> {});

    assertTrue(firstStarted.await(2, TimeUnit.SECONDS), "first task should have started");
    loader.load(
        () -> {
          order.add(2);
          return 2;
        },
        val -> secondDone.countDown(),
        ex -> secondDone.countDown());

    assertTrue(secondDone.await(3, TimeUnit.SECONDS));
    assertEquals(java.util.List.of(1, 2), order);
  }

  @Test
  void supersededLoadDoesNotDeliverAfterNewLoadStarts() throws Exception {
    var loader = new DataLoader<String>();
    var delivered = new java.util.concurrent.CopyOnWriteArrayList<String>();
    var firstStarted = new CountDownLatch(1);
    var firstMayFinish = new CountDownLatch(1);
    var secondDone = new CountDownLatch(1);

    loader.load(
        () -> {
          firstStarted.countDown();
          while (true) {
            try {
              firstMayFinish.await();
              break;
            } catch (InterruptedException e) {
              // Keep running: models a fetch that ignores interruption.
            }
          }
          return "stale";
        },
        delivered::add,
        ex -> delivered.add("stale-error"));

    assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
    loader.load(
        () -> "fresh",
        val -> {
          delivered.add(val);
          secondDone.countDown();
        },
        ex -> secondDone.countDown());
    firstMayFinish.countDown();

    assertTrue(secondDone.await(3, TimeUnit.SECONDS));
    assertEquals(java.util.List.of("fresh"), delivered);
  }
}
