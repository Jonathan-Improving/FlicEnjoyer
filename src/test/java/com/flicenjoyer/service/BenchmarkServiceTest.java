package com.flicenjoyer.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BenchmarkServiceTest {

  private static final String[] IDS = {"a", "b", "c"};

  @Test
  void benchmarkComputesStatistics() throws Exception {
    BenchmarkService.BenchmarkResult result =
        BenchmarkService.benchmark(
            () -> {
              // simulate ~1ms work
              Thread.sleep(1);
            },
            100);

    assertTrue(result.medianMs() > 0);
    assertTrue(result.p95Ms() >= result.medianMs());
    assertTrue(result.p99Ms() >= result.p95Ms());
    assertTrue(result.opsPerSecond() > 0);
  }

  @Test
  void benchmarkHandlesZeroWorkTask() throws Exception {
    BenchmarkService.BenchmarkResult result = BenchmarkService.benchmark(() -> {}, 50);

    assertTrue(result.medianMs() >= 0);
    assertTrue(result.opsPerSecond() > 0);
  }

  @Test
  void benchmarkReportsProgressPerIteration() throws Exception {
    List<Integer> progress = new CopyOnWriteArrayList<>();
    BenchmarkService.benchmark(() -> {}, 5, progress::add);

    assertEquals(List.of(1, 2, 3, 4, 5), progress);
  }

  @Test
  void concurrentThroughputRunsWarmupPlusTimedOps() throws Exception {
    var runs = new AtomicInteger();
    var lastProgress = new AtomicInteger();

    double ops =
        BenchmarkService.concurrentThroughput(runs::incrementAndGet, 4, 100, lastProgress::set);

    assertEquals(4 * 100, runs.get());
    assertEquals(4 * 90, lastProgress.get());
    assertTrue(ops > 0);
  }

  @Test
  void concurrentThroughputSurvivesFailingTask() throws Exception {
    double ops =
        BenchmarkService.concurrentThroughput(
            () -> {
              throw new IllegalStateException("boom");
            },
            2,
            20,
            null);

    assertEquals(0.0, ops);
  }

  @Test
  void concurrentComparisonReportsSpeedupAndMonotonicProgress() throws Exception {
    List<Integer> progress = new CopyOnWriteArrayList<>();

    var result =
        BenchmarkService.concurrentComparison(
            () -> Thread.sleep(1), () -> {}, 2, 20, progress::add);

    assertEquals(2, result.threads());
    assertTrue(result.dbOpsPerSecond() > 0);
    assertTrue(result.valkeyOpsPerSecond() > result.dbOpsPerSecond());
    assertEquals(result.valkeyOpsPerSecond() / result.dbOpsPerSecond(), result.speedup(), 1e-9);
    assertEquals(2 * 18 * 2, progress.getLast());
  }

  @Test
  void catalogTasksUseSevenToOneReadWriteMix() throws Exception {
    var catalog = mock(CatalogService.class);
    var history = mock(WatchHistoryService.class);
    var pair = BenchmarkService.createTasks("catalog", IDS, catalog, history);

    for (int i = 0; i < 16; i++) {
      pair.dbTask().run();
      pair.valkeyTask().run();
    }

    verify(catalog, times(14)).getByIdFromDb(anyString());
    verify(catalog, times(2)).updateRatingInDbOnly(anyString(), anyDouble());
    verify(catalog, times(14)).getById(anyString());
    verify(catalog, times(2)).updateRating(anyString(), anyDouble());
  }

  @Test
  void resumeTasksUseSevenToOneReadWriteMix() throws Exception {
    var catalog = mock(CatalogService.class);
    var history = mock(WatchHistoryService.class);
    var pair = BenchmarkService.createTasks("resume", IDS, catalog, history);

    for (int i = 0; i < 16; i++) {
      pair.dbTask().run();
      pair.valkeyTask().run();
    }

    verify(history, times(14)).getResumePointFromDb(anyString());
    verify(history, times(2)).updateResumePointInDbOnly(anyString(), anyLong());
    verify(history, times(14)).getResumePoint(anyString());
    verify(history, times(2)).updateResumePoint(anyString(), anyLong());
  }

  @Test
  void searchTasksCompareDbAgainstFts() throws Exception {
    var catalog = mock(CatalogService.class);
    var history = mock(WatchHistoryService.class);
    var pair = BenchmarkService.createTasks("Search (ValkeySearch FTS)", IDS, catalog, history);

    pair.dbTask().run();
    pair.valkeyTask().run();

    verify(catalog).searchFromDb(anyString(), anyInt());
    verify(catalog).searchViaFts(anyString(), anyInt());
    verify(catalog, never()).getById(anyString());
  }
}
