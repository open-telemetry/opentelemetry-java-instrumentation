package com.datadog.profiling.exceptions;

import static java.lang.Math.abs;
import static java.lang.Math.round;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.when;

import datadog.common.exec.CommonTaskExecutor;
import datadog.common.exec.CommonTaskExecutor.Task;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Test various hand crafted scenarios of events coming in different patterns. Test both, the
 * isolated single threaded execution as well as events arriving on concurrent threads.
 *
 * <p>The test supports 'benchmark' mode to explore the reliability boundaries where all test cases
 * can be run multiple times - the number of iteration is passed in in {@literal
 * com.datadog.profiling.exceptions.test-iterations} system property.
 */
@ExtendWith(MockitoExtension.class)
@Slf4j
class StreamingSamplerTest {

  private static final Duration WINDOW_DURATION = Duration.ofSeconds(1);

  /** Generates windows with numbers of events according to Poisson distribution */
  private static final class PoissonWindowEventsSupplier implements Supplier<Integer> {
    private final PoissonDistribution distribution;

    /** @param eventsPerWindowMean the average number of events per window */
    PoissonWindowEventsSupplier(final int eventsPerWindowMean) {
      distribution = new PoissonDistribution(eventsPerWindowMean);
      distribution.reseedRandomGenerator(12345671);
    }

    @Override
    public Integer get() {
      return distribution.sample();
    }

    @Override
    public String toString() {
      return "Poisson: ("
          + "mean="
          + distribution.getMean()
          + ", variance="
          + distribution.getNumericalVariance()
          + ")";
    }
  }

  /**
   * Generates bursty windows - some of the windows have extremely low number of events while the
   * others have very hight number of events.
   */
  private static final class BurstingWindowsEventsSupplier implements Supplier<Integer> {
    private final Random rnd = new Random(176431);

    private final double burstProbability;
    private final int minEvents;
    private final int maxEvents;

    /**
     * @param burstProbability the probability of burst window happening
     * @param nonBurstEvents number of events in non-burst window
     * @param burstEvents number of events in burst window
     */
    BurstingWindowsEventsSupplier(
        final double burstProbability, final int nonBurstEvents, final int burstEvents) {
      this.burstProbability = burstProbability;
      this.minEvents = nonBurstEvents;
      this.maxEvents = burstEvents;
    }

    @Override
    public Integer get() {
      if (rnd.nextDouble() <= burstProbability) {
        return maxEvents;
      } else {
        return minEvents;
      }
    }

    @Override
    public String toString() {
      return "Burst: ("
          + "probability="
          + burstProbability
          + ", minEvents="
          + minEvents
          + ", maxEvents="
          + maxEvents
          + ')';
    }
  }

  /** Generates windows with constant number of events. */
  private static final class ConstantWindowsEventsSupplier implements Supplier<Integer> {
    private final int events;

    /** @param events number of events per window */
    ConstantWindowsEventsSupplier(final int events) {
      this.events = events;
    }

    @Override
    public Integer get() {
      return events;
    }

    @Override
    public String toString() {
      return "Constant: (" + "events=" + events + ')';
    }
  }

  /** Generates a pre-configured repeating sequence of window events */
  private static final class RepeatingWindowsEventsSupplier implements Supplier<Integer> {
    private final int[] eventsCounts;
    private int pointer = 0;

    /** @param windowEvents an array of number of events per each window in the sequence */
    RepeatingWindowsEventsSupplier(final int... windowEvents) {
      this.eventsCounts = Arrays.copyOf(windowEvents, windowEvents.length);
    }

    @Override
    public Integer get() {
      try {
        return eventsCounts[pointer];
      } finally {
        pointer = (pointer + 1) % eventsCounts.length;
      }
    }

    @Override
    public String toString() {
      return "Repeating: (" + "definition=" + Arrays.toString(eventsCounts) + ')';
    }
  }

  private static class WindowSamplingResult {
    final int events;
    final int samples;
    final double sampleIndexSkew;

    WindowSamplingResult(int events, int samples, double sampleIndexSkew) {
      this.events = events;
      this.samples = samples;
      this.sampleIndexSkew = sampleIndexSkew;
    }
  }

  private static final StandardDeviation STANDARD_DEVIATION = new StandardDeviation();
  private static final Mean MEAN = new Mean();
  private static final int WINDOWS = 120;
  private static final int SAMPLES_PER_WINDOW = 100;
  private static final int LOOKBACK = 30;

  @Mock CommonTaskExecutor taskExecutor;
  @Captor ArgumentCaptor<Task<StreamingSampler>> rollWindowTaskCaptor;
  @Captor ArgumentCaptor<StreamingSampler> rollWindowTargetCaptor;
  @Mock ScheduledFuture scheduledFuture;

  @BeforeEach
  public void setup() {
    when(taskExecutor.scheduleAtFixedRate(
            rollWindowTaskCaptor.capture(),
            rollWindowTargetCaptor.capture(),
            eq(WINDOW_DURATION.toNanos()),
            eq(WINDOW_DURATION.toNanos()),
            same(TimeUnit.NANOSECONDS),
            any()))
        .thenReturn(scheduledFuture);
  }

  @Test
  public void testBurstLowProbability() throws Exception {
    testSampler(new BurstingWindowsEventsSupplier(0.1d, 5, 5000), 40);
  }

  @Test
  public void testBurstHighProbability() throws Exception {
    testSampler(new BurstingWindowsEventsSupplier(0.8d, 5, 5000), 20);
  }

  @Test
  public void testPoissonLowFrequency() throws Exception {
    testSampler(new PoissonWindowEventsSupplier(153), 15);
  }

  @Test
  public void testPoissonMidFrequency() throws Exception {
    testSampler(new PoissonWindowEventsSupplier(283), 15);
  }

  @Test
  public void testPoissonHighFrequency() throws Exception {
    testSampler(new PoissonWindowEventsSupplier(1013), 15);
  }

  @Test
  public void testConstantVeryLowLoad() throws Exception {
    testSampler(new ConstantWindowsEventsSupplier(1), 10);
  }

  @Test
  public void testConstantLowLoad() throws Exception {
    testSampler(new ConstantWindowsEventsSupplier(153), 15);
  }

  @Test
  public void testConstantMediumLoad() throws Exception {
    testSampler(new ConstantWindowsEventsSupplier(713), 15);
  }

  @Test
  public void testConstantHighLoad() throws Exception {
    testSampler(new ConstantWindowsEventsSupplier(5211), 15);
  }

  @Test
  public void testRepeatingSemiRandom() throws Exception {
    testSampler(
        new RepeatingWindowsEventsSupplier(180, 200, 0, 0, 0, 1500, 1000, 430, 200, 115, 115, 900),
        15);
  }

  @Test
  public void testRepeatingRegularStartWithBurst() throws Exception {
    testSampler(new RepeatingWindowsEventsSupplier(1000, 0, 1000, 0, 1000, 0), 15);
  }

  @Test
  public void testRepeatingRegularStartWithLow() throws Exception {
    testSampler(new RepeatingWindowsEventsSupplier(0, 1000, 0, 1000, 0, 1000), 15);
  }

  private void testSampler(final Supplier<Integer> windowEventsSupplier, final int maxErrorPercent)
      throws Exception {
    int iterations =
        Integer.parseInt(
            System.getProperty("com.datadog.profiling.exceptions.test-iterations", "1"));
    for (int i = 0; i < iterations; i++) {
      testSamplerInline(windowEventsSupplier, maxErrorPercent);
      for (int numOfThreads = 1; numOfThreads <= 64; numOfThreads *= 2) {
        testSamplerConcurrently(numOfThreads, windowEventsSupplier, maxErrorPercent);
      }
    }
  }

  private void testSamplerInline(
      final Supplier<Integer> windowEventsSupplier, final int maxErrorPercent) {
    log.info(
        "> mode: {}, windows: {}, SAMPLES_PER_WINDOW: {}, LOOKBACK: {}, max error: {}%",
        windowEventsSupplier, WINDOWS, SAMPLES_PER_WINDOW, LOOKBACK, maxErrorPercent);
    final StreamingSampler sampler =
        new StreamingSampler(WINDOW_DURATION, SAMPLES_PER_WINDOW, LOOKBACK, taskExecutor);

    // simulate event generation and sampling for the given number of sampling windows
    final long expectedSamples = WINDOWS * SAMPLES_PER_WINDOW;

    long allSamples = 0L;
    long allEvents = 0L;

    final double[] samplesPerWindow = new double[WINDOWS];
    final double[] sampleIndexSkewPerWindow = new double[WINDOWS];
    for (int w = 0; w < WINDOWS; w++) {
      final long samplesBase = 0L;
      WindowSamplingResult result = generateWindowEventsAndSample(windowEventsSupplier, sampler);
      samplesPerWindow[w] =
          (1 - abs((result.samples - samplesBase - expectedSamples) / (double) expectedSamples));
      sampleIndexSkewPerWindow[w] = result.sampleIndexSkew;
      allSamples += result.samples;
      allEvents += result.events;

      rollWindow();
    }

    /*
     * Turn all events into samples if their number is <= than the expected number of samples.
     */
    final double targetSamples = Math.min(allEvents, expectedSamples);

    /*
     * Calculate the percentual error based on the expected and the observed number of samples.
     */
    final double percentualError = round(((targetSamples - allSamples) / targetSamples) * 100);

    reportSampleStatistics(samplesPerWindow, targetSamples, percentualError);
    reportSampleIndexSkew(sampleIndexSkewPerWindow);

    assertTrue(
        abs(percentualError) <= maxErrorPercent,
        "abs(("
            + targetSamples
            + " - "
            + allSamples
            + ") / "
            + targetSamples
            + ")% > "
            + maxErrorPercent
            + "%");
  }

  private void reportSampleStatistics(
      double[] samplesPerWindow, double targetSamples, double percentualError) {
    final double samplesPerWindowMean = MEAN.evaluate(samplesPerWindow);
    final double samplesPerWindowStdev =
        STANDARD_DEVIATION.evaluate(samplesPerWindow, samplesPerWindowMean);

    log.info(
        "\t per window samples = (avg: {}, stdev: {}, estimated total: {})",
        samplesPerWindowMean,
        samplesPerWindowStdev,
        targetSamples);

    log.info("\t percentual error = {}%", percentualError);
  }

  private void reportSampleIndexSkew(double[] sampleIndexSkewPerWindow) {
    Pair<Double, Double> skewIndicators = calculateSkewIndicators(sampleIndexSkewPerWindow);
    log.info(
        "\t avg window skew interval = <-{}%, {}%>",
        round(skewIndicators.getFirst() * 100), round(skewIndicators.getSecond() * 100));
  }

  /**
   * Simulate the number of events per window. Perform sampling and capture the number of observed
   * events and samples.
   *
   * @param windowEventsSupplier events generator implementation
   * @param sampler sampler instance
   * @return a {@linkplain WindowSamplingResult} instance capturing the number of observed events,
   *     samples and the sample index skew
   */
  private WindowSamplingResult generateWindowEventsAndSample(
      Supplier<Integer> windowEventsSupplier, StreamingSampler sampler) {
    List<Integer> sampleIndices = new ArrayList<>();
    int samples = 0;
    int events = windowEventsSupplier.get();
    for (int i = 0; i < events; i++) {
      if (sampler.sample()) {
        sampleIndices.add(i);
        samples++;
      }
    }
    double sampleIndexMean = MEAN.evaluate(toDoubleArray(sampleIndices));
    double sampleIndexSkew = events != 0 ? sampleIndexMean / events : 0;
    return new WindowSamplingResult(events, samples, sampleIndexSkew);
  }

  /**
   * Calculate the sample index skew boundaries. A 'sample index skew' is defined as the distance of
   * the average sample index in each window from the mean event index in the same window. Given the
   * range of the event indices 1..N, the event index mean M calculated as (N - 1)/2 and the sample
   * index mean S the skew K is calculated as 'K = M - S'. This gives the skew range of &lt;-0.5,
   * 0.5&gt;.
   *
   * <p>If the samples are spread out completely regularly the skew would be 0. If the beginning of
   * the window is favored the skew would be negative and if the tail of the window is favored the
   * skew would be positive.
   *
   * @param sampleIndexSkewPerWindow the index skew per window
   * @return a min-max boundaries for the sample index skew
   */
  private Pair<Double, Double> calculateSkewIndicators(double[] sampleIndexSkewPerWindow) {
    double skewPositiveAvg = 0d;
    double skewNegativeAvg = 0d;
    int negativeCount = 0;
    for (final double skew : sampleIndexSkewPerWindow) {
      if (skew >= 0.5d) {
        skewPositiveAvg += skew - 0.5d;
      } else {
        negativeCount++;
        skewNegativeAvg += 0.5d - skew;
      }
    }
    final int positiveCount = sampleIndexSkewPerWindow.length - negativeCount;
    if (positiveCount > 0) {
      skewPositiveAvg /= sampleIndexSkewPerWindow.length - negativeCount;
    }
    if (negativeCount > 0) {
      skewNegativeAvg /= negativeCount;
    }
    return new Pair<>(skewNegativeAvg, skewPositiveAvg);
  }

  private static double[] toDoubleArray(final List<? extends Number> data) {
    return data.stream().mapToDouble(Number::doubleValue).toArray();
  }

  private void testSamplerConcurrently(
      final int threadCount,
      final Supplier<Integer> windowEventsSupplier,
      final int maxErrorPercent)
      throws Exception {
    log.info(
        "> threads: {}, mode: {}, windows: {}, SAMPLES_PER_WINDOW: {}, LOOKBACK: {}, max error: {}",
        threadCount,
        windowEventsSupplier,
        WINDOWS,
        SAMPLES_PER_WINDOW,
        LOOKBACK,
        maxErrorPercent);

    /*
     * This test attempts to simulate concurrent computations by making sure that sampling requests and the window maintenance routine are run in parallel.
     * It does not provide coverage of all possible execution sequences but should be good enough for getting the 'ballpark' numbers.
     */
    final long expectedSamples = SAMPLES_PER_WINDOW * WINDOWS;
    final AtomicLong allSamples = new AtomicLong(0);
    final AtomicLong receivedEvents = new AtomicLong(0);

    final StreamingSampler sampler =
        new StreamingSampler(WINDOW_DURATION, SAMPLES_PER_WINDOW, LOOKBACK, taskExecutor);

    for (int w = 0; w < WINDOWS; w++) {
      final Thread[] threads = new Thread[threadCount];
      for (int i = 0; i < threadCount; i++) {
        threads[i] =
            new Thread(
                () -> {
                  WindowSamplingResult samplingResult =
                      generateWindowEventsAndSample(windowEventsSupplier, sampler);
                  allSamples.addAndGet(samplingResult.samples);
                  receivedEvents.addAndGet(samplingResult.events);
                });
      }

      for (final Thread t : threads) {
        t.start();
      }
      for (final Thread t : threads) {
        t.join();
      }
      rollWindow();
    }

    final long samples = allSamples.get();
    /*
     * Turn all events into samples if their number is <= than the expected number of samples.
     */
    final long targetSamples = Math.min(expectedSamples, receivedEvents.get());
    /*
     * Calculate the percentual error based on the expected and the observed number of samples.
     */
    final int percentualError = round(((targetSamples - samples) / (float) targetSamples) * 100);
    log.info("\t percentual error = {}%", percentualError);

    assertTrue(
        abs(percentualError) <= maxErrorPercent,
        "abs(("
            + expectedSamples
            + " - "
            + samples
            + ") / "
            + expectedSamples
            + ")% > "
            + maxErrorPercent
            + "%");
  }

  private void rollWindow() {
    rollWindowTaskCaptor.getValue().run(rollWindowTargetCaptor.getValue());
  }
}
