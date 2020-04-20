package com.datadog.profiling.exceptions;

import datadog.common.exec.CommonTaskExecutor;
import datadog.common.exec.CommonTaskExecutor.Task;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * A streaming (non-remembering) sampler.
 *
 * <p>The sampler attempts to generate at most N samples per fixed time window in randomized
 * fashion. For this it divides the timeline into 'sampling windows' of constant duration. Each
 * sampling window targets a constant number of samples which are scattered randomly (uniform
 * distribution) throughout the window duration and once the window is over the real stats of
 * incoming events and the number of gathered samples is used to recompute the target probability to
 * use in the following window.
 *
 * <p>This will guarantee, if the windows are not excessively large, that the sampler will be able
 * to adjust to the changes in the rate of incoming events.
 *
 * <p>However, there might so rapid changes in incoming events rate that we will optimistically use
 * all allowed samples well before the current window has elapsed or, on the other end of the
 * spectrum, there will be to few incoming events and the sampler will not be able to generate the
 * target number of samples.
 *
 * <p>To smooth out these hicups the sampler maintains an under-sampling budget which can be used
 * to compensate for too rapid changes in the incoming events rate and maintain the target average
 * number of samples per window.
 */
class StreamingSampler {

  /*
   * Number of windows to look back when computing carried over budget.
   * This value is `approximate' since we use EMA to keep running average.
   */
  private static final int CARRIED_OVER_BUDGET_LOOK_BACK = 16;

  private static final class Counts {
    private final LongAdder testCounter = new LongAdder();
    private final AtomicLong sampleCounter = new AtomicLong(0L);

    void addTest() {
      testCounter.increment();
    }

    boolean addSample(final long limit) {
      return sampleCounter.getAndUpdate(s -> s + (s < limit ? 1 : 0)) < limit;
    }
  }

  /*
   * Exponential Moving Average (EMA) last element weight.
   * Check out papers about using EMA for streaming data - eg.
   * https://nestedsoftware.com/2018/04/04/exponential-moving-average-on-streaming-data-4hhl.24876.html
   *
   * Corresponds to 'lookback' of N values:
   * With T being the index of the most recent value the lookback of N values means that for all values with index
   * T-K, where K > N, the relative weight of that value computed as (1 - alpha)^K is less or equal than the
   * weight assigned by a plain arithmetic average (= 1/N).
   */
  private final double emaAlpha;
  private final int samplesPerWindow;

  private final AtomicReference<Counts> countsRef;

  // these attributes need to be volatile since they are accessed from user threds as well as the
  // maintenance one
  private volatile double probability = 1d;
  private volatile long samplesBudget;

  // these attributes are accessed solely from the window maintenance thread
  private double totalCountRunningAverage = 0d;
  private double avgSamples;

  private final double budgetAlpha;

  /**
   * Create a new sampler instance
   *
   * @param windowDuration the sampling window duration
   * @param samplesPerWindow the maximum number of samples in the sampling window
   * @param lookback the number of windows to consider in averaging the sampling rate
   * @param taskExecutor common task executor to use for periodic rolls
   */
  StreamingSampler(
      final Duration windowDuration,
      final int samplesPerWindow,
      final int lookback,
      final CommonTaskExecutor taskExecutor) {

    this.samplesPerWindow = samplesPerWindow;
    samplesBudget = samplesPerWindow + CARRIED_OVER_BUDGET_LOOK_BACK * samplesPerWindow;
    emaAlpha = computeIntervalAlpha(lookback);
    budgetAlpha = computeIntervalAlpha(CARRIED_OVER_BUDGET_LOOK_BACK);
    countsRef = new AtomicReference<>(new Counts());

    taskExecutor.scheduleAtFixedRate(
        RollWindowTask.INSTANCE,
        this,
        windowDuration.toNanos(),
        windowDuration.toNanos(),
        TimeUnit.NANOSECONDS,
        "exception sampling window roll");
  }

  /**
   * Create a new sampler instance with automatic window roll.
   *
   * @param windowDuration the sampling window duration
   * @param samplesPerWindow the maximum number of samples in the sampling window
   * @param lookback the number of windows to consider in averaging the sampling rate
   */
  StreamingSampler(final Duration windowDuration, final int samplesPerWindow, final int lookback) {
    this(windowDuration, samplesPerWindow, lookback, CommonTaskExecutor.INSTANCE);
  }

  /**
   * Provides binary answer whether the current event is to be sampled
   *
   * @return {@literal true} if the event should be sampled
   */
  final boolean sample() {
    final Counts counts = countsRef.get();
    counts.addTest();
    if (ThreadLocalRandom.current().nextDouble() < probability) {
      return counts.addSample(samplesBudget);
    }

    return false;
  }

  private void rollWindow() {

    /*
     * Atomically replace the Counts instance such that sample requests during window maintenance will be
     * using the newly created counts instead of the ones currently processed by the maintenance routine.
     */
    final Counts counts = countsRef.getAndSet(new Counts());
    final long totalCount = counts.testCounter.sum();
    final long sampledCount = counts.sampleCounter.get();

    samplesBudget = calculateBudgetEma(sampledCount);

    if (totalCountRunningAverage == 0) {
      totalCountRunningAverage = totalCount;
    } else {
      totalCountRunningAverage =
          totalCountRunningAverage + emaAlpha * (totalCount - totalCountRunningAverage);
    }

    if (totalCountRunningAverage <= 0) {
      probability = 1;
    } else {
      probability = Math.min(samplesBudget / totalCountRunningAverage, 1d);
    }
  }

  private long calculateBudgetEma(final long sampledCount) {
    avgSamples =
        Double.isNaN(avgSamples)
            ? sampledCount
            : avgSamples + budgetAlpha * (sampledCount - avgSamples);
    return Math.round(Math.max(samplesPerWindow - avgSamples, 0) * CARRIED_OVER_BUDGET_LOOK_BACK);
  }

  private static double computeIntervalAlpha(final int lookback) {
    return 1 - Math.pow(lookback, -1d / lookback);
  }

  /*
   * Important to use explicit class to avoid implicit hard references to StreamingSampler from within scheduler
   */
  private static class RollWindowTask implements Task<StreamingSampler> {

    static final RollWindowTask INSTANCE = new RollWindowTask();

    @Override
    public void run(final StreamingSampler target) {
      target.rollWindow();
    }
  }
}
