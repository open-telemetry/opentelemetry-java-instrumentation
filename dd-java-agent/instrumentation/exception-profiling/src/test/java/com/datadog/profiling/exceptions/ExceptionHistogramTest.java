package com.datadog.profiling.exceptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import datadog.trace.api.Config;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Phaser;
import java.util.stream.Stream;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openjdk.jmc.common.item.Aggregators;
import org.openjdk.jmc.common.item.Attribute;
import org.openjdk.jmc.common.item.IAttribute;
import org.openjdk.jmc.common.item.IItemCollection;
import org.openjdk.jmc.common.item.ItemFilters;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;

public class ExceptionHistogramTest {

  private static final IAttribute<String> TYPE =
      Attribute.attr("type", "type", "Exception type", UnitLookup.PLAIN_TEXT);
  private static final IAttribute<IQuantity> COUNT =
      Attribute.attr("count", "count", "Exception count", UnitLookup.NUMBER);

  private static final Comparator<Exception> EXCEPTION_COMPARATOR =
      new Comparator<Exception>() {
        @Override
        public int compare(final Exception e1, final Exception e2) {
          return e1.getClass().getCanonicalName().compareTo(e2.getClass().getCanonicalName());
        }

        @Override
        public boolean equals(final Object obj) {
          return this == obj;
        }
      };

  private static final int MAX_ITEMS = 2;
  private static final int MAX_SIZE = 2;

  private Recording recording;
  private Recording snapshot;
  private ExceptionHistogram instance;

  @BeforeEach
  public void setup() {
    recording = new Recording();
    recording.enable("datadog.ExceptionCount");
    recording.start();

    final Properties properties = new Properties();
    properties.setProperty(
        Config.PROFILING_EXCEPTION_HISTOGRAM_TOP_ITEMS, Integer.toString(MAX_ITEMS));

    instance = new ExceptionHistogram(Config.get(properties));
  }

  @AfterEach
  public void tearDown() {
    if (snapshot != null) {
      snapshot.close();
    }
    recording.close();
    instance.deregister();
  }

  @Test
  public void testFirstHitConcurrent() {
    Phaser phaser = new Phaser(2);

    ExceptionHistogram histogram =
        new ExceptionHistogram(Config.get()) {
          @Override
          void emitEvents(Stream<ExceptionHistogram.Pair<String, Long>> items) {
            super.emitEvents(items);
            // #1 - histo sums are reset but 0 entries not removed yet
            phaser.arriveAndAwaitAdvance();
            // #2 - safe to leave the emit() method
            phaser.arriveAndAwaitAdvance();
          }
        };
    // don't want the JFR integration active here
    histogram.deregister();
    for (int i = 0; i < 5; i++) {
      boolean firstHit = histogram.record(new NullPointerException());
      assertEquals(i == 0, firstHit);
    }

    // start emitting in a separate thread
    new Thread(histogram::doEmit).start();
    // wait for #1 - this is the point where data race can happen if new exceptions are recording
    // during 'emit()'
    phaser.arriveAndAwaitAdvance();
    // make sure that any exception recording during 'emit()' has a correct 'first hit' status
    assertTrue(histogram.record(new NullPointerException()));
    // unblock #2 such that 'emit()' may continue
    phaser.arrive();

    // the subsequent exception recording will not be a 'first hit'
    assertFalse(histogram.record(new NullPointerException()));
  }

  @Test
  public void testExceptionsRecorded()
      throws IOException, CouldNotLoadRecordingException, InterruptedException {
    writeExceptions(
        ImmutableMap.of(
            new NullPointerException(),
            8,
            new IllegalArgumentException(),
            5,
            new RuntimeException(),
            1));

    final Instant firstRecordingNow = Instant.now();
    snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    final IItemCollection firstRecording = getEvents(snapshot, Instant.MIN, firstRecordingNow);

    assertEquals(MAX_ITEMS, firstRecording.getAggregate(Aggregators.count()).longValue());
    assertEquals(
        8,
        firstRecording
            .apply(ItemFilters.equals(TYPE, NullPointerException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    assertEquals(
        5,
        firstRecording
            .apply(ItemFilters.equals(TYPE, IllegalArgumentException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    snapshot.close();

    // Sleep to make sure we get new batch of exceptions only
    Thread.sleep(1000);

    writeExceptions(
        ImmutableMap.of(
            new RuntimeException(),
            8,
            new NullPointerException(),
            5,
            new IllegalArgumentException(),
            1));

    snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    final IItemCollection secondRecording =
        getEvents(snapshot, firstRecordingNow.plusMillis(1000), Instant.MAX);

    assertEquals(MAX_ITEMS, secondRecording.getAggregate(Aggregators.count()).longValue());
    assertEquals(
        8,
        secondRecording
            .apply(ItemFilters.equals(TYPE, RuntimeException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    assertEquals(
        5,
        secondRecording
            .apply(ItemFilters.equals(TYPE, NullPointerException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    snapshot.close();
  }

  @Test
  public void testHistogramSizeIsLimited()
      throws IOException, CouldNotLoadRecordingException, InterruptedException {
    instance.deregister();
    final Properties properties = new Properties();
    properties.setProperty(
        Config.PROFILING_EXCEPTION_HISTOGRAM_MAX_COLLECTION_SIZE, Integer.toString(MAX_SIZE));

    instance = new ExceptionHistogram(Config.get(properties));

    // Exceptions are written in alphabetical order
    writeExceptions(
        ImmutableSortedMap.copyOf(
            ImmutableMap.of(
                new Exception(),
                5,
                new IllegalArgumentException(),
                8,
                new NegativeArraySizeException(),
                10,
                new NullPointerException(),
                11),
            EXCEPTION_COMPARATOR));

    final Instant firstRecordingNow = Instant.now();
    snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    final IItemCollection firstRecording = getEvents(snapshot, Instant.MIN, firstRecordingNow);

    assertEquals(MAX_ITEMS + 1, firstRecording.getAggregate(Aggregators.count()).longValue());
    assertEquals(
        5,
        firstRecording
            .apply(ItemFilters.equals(TYPE, Exception.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    assertEquals(
        8,
        firstRecording
            .apply(ItemFilters.equals(TYPE, IllegalArgumentException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    assertEquals(
        21,
        firstRecording
            .apply(ItemFilters.equals(TYPE, ExceptionHistogram.CLIPPED_ENTRY_TYPE_NAME))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    snapshot.close();

    // Sleep to make sure we get new batch of exceptions only
    Thread.sleep(1000);

    // Exceptions are written in 'code' order
    writeExceptions(
        ImmutableSortedMap.copyOf(
            ImmutableMap.of(
                new IllegalArgumentException(),
                5,
                new NegativeArraySizeException(),
                8,
                new NullPointerException(),
                10,
                new RuntimeException(),
                11),
            EXCEPTION_COMPARATOR));

    snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    final IItemCollection secondRecording =
        getEvents(snapshot, firstRecordingNow.plusMillis(1000), Instant.MAX);

    assertEquals(MAX_ITEMS + 1, secondRecording.getAggregate(Aggregators.count()).longValue());
    assertEquals(
        5,
        secondRecording
            .apply(ItemFilters.equals(TYPE, IllegalArgumentException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    assertEquals(
        8,
        secondRecording
            .apply(ItemFilters.equals(TYPE, NegativeArraySizeException.class.getCanonicalName()))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    assertEquals(
        21,
        firstRecording
            .apply(ItemFilters.equals(TYPE, ExceptionHistogram.CLIPPED_ENTRY_TYPE_NAME))
            .getAggregate(Aggregators.sum(COUNT))
            .longValue());
    snapshot.close();
  }

  @Test
  public void testDisabled() throws IOException, CouldNotLoadRecordingException {
    recording.disable("datadog.ExceptionCount");
    final Map<Exception, Integer> exceptions =
        ImmutableMap.of(
            new NullPointerException(),
            8,
            new IllegalArgumentException(),
            5,
            new RuntimeException(),
            1);

    for (final Map.Entry<Exception, Integer> entry : exceptions.entrySet()) {
      for (int i = 0; i < entry.getValue(); i++) {
        assertFalse(instance.record(entry.getKey()));
      }
    }

    final Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    final IItemCollection recording = getEvents(snapshot, Instant.MIN, Instant.MAX);

    assertEquals(0, recording.getAggregate(Aggregators.count()).longValue());

    snapshot.close();
  }

  private IItemCollection getEvents(
      final Recording secondSnapshot, final Instant start, final Instant end)
      throws IOException, CouldNotLoadRecordingException {
    return JfrLoaderToolkit.loadEvents(secondSnapshot.getStream(start, end))
        .apply(ItemFilters.type("datadog.ExceptionCount"));
  }

  private void writeExceptions(final Map<Exception, Integer> exceptions) {
    // Just check that writing null doesn't break anything
    instance.record(null);

    for (final Map.Entry<Exception, Integer> entry : exceptions.entrySet()) {
      for (int i = 0; i < entry.getValue(); i++) {
        instance.record(entry.getKey());
      }
    }
  }
}
