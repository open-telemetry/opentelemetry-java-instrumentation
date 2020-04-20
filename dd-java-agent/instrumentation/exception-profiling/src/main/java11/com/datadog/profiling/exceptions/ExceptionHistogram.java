package com.datadog.profiling.exceptions;

import datadog.trace.api.Config;
import jdk.jfr.EventType;
import jdk.jfr.FlightRecorder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

/**
 * A simple exception type histogram implementation.<br>
 * It tracks a fixed number of exception types and for each of them it keeps the number of instances created since
 * the last {@linkplain ExceptionHistogram#emit()} call (or creating a new {@linkplain ExceptionHistogram} instance
 * if {@linkplain ExceptionHistogram#emit()} hasn't been called yet).<br>
 *
 * An {@linkplain ExceptionHistogram} instance is registered with JFR to call {@linkplain ExceptionHistogram#emit()}
 * method at chunk end, as specified in {@linkplain ExceptionCountEvent} class. This callback will then emit a number
 * of {@linkplain ExceptionCountEvent} events.
 */
@Slf4j
public class ExceptionHistogram {

  static final String CLIPPED_ENTRY_TYPE_NAME = "TOO-MANY-EXCEPTIONS";

  private final Map<String, AtomicLong> histogram = new ConcurrentHashMap<>();
  private final int maxTopItems;
  private final int maxSize;
  private final EventType exceptionCountEventType;
  private final Runnable eventHook;

  ExceptionHistogram(final Config config) {
    maxTopItems = config.getProfilingExceptionHistogramTopItems();
    maxSize = config.getProfilingExceptionHistogramMaxCollectionSize();
    exceptionCountEventType = EventType.getEventType(ExceptionCountEvent.class);
    eventHook = this::emit;
    FlightRecorder.addPeriodicEvent(ExceptionCountEvent.class, eventHook);
  }

  /**
   * Remove this instance from JFR periodic events callbacks
   */
  void deregister() {
    FlightRecorder.removePeriodicEvent(eventHook);
  }

  /**
   * Record a new exception instance
   * @param exception instance
   * @return {@literal true} if this is the first record of the given exception type; {@literal false} otherwise
   */
  public boolean record(final Exception exception) {
    if (exception == null) {
      return false;
    }
    return record(exception.getClass().getCanonicalName());
  }

  private boolean record(String typeName) {
    if (!exceptionCountEventType.isEnabled()) {
      return false;
    }
    if (!histogram.containsKey(typeName) && histogram.size() >= maxSize) {
      log.debug("Histogram is too big, skipping adding new entry: {}", typeName);
      // Overwrite type name to limit total number of entries in the histogram
      typeName = CLIPPED_ENTRY_TYPE_NAME;
    }

    long count = histogram
      .computeIfAbsent(
        typeName,
        k -> new AtomicLong()
      )
      .getAndIncrement();

    /*
     * This is supposed to signal that a particular exception type was seen the first time in a particular time span.
     * !ATTENTION! This will work on best-effort basis - namely all overflowing exception which are recorded
     * as 'TOO-MANY-EXCEPTIONS' will receive only one common 'first hit'.
     */
    return count == 0;
  }

  private void emit() {
    if (!exceptionCountEventType.isEnabled()) {
      return;
    }

    doEmit();
  }

  void doEmit() {
    Stream<Pair<String, Long>> items =
      histogram
        .entrySet()
        .stream()
        .map(e -> Pair.of(e.getKey(), e.getValue().getAndSet(0)))
        .filter(p -> p.getValue() != 0)
        .sorted((l1, l2) -> Long.compare(l2.getValue(), l1.getValue()));

    if (maxTopItems > 0) {
      items = items.limit(maxTopItems);
    }

    emitEvents(items);

    // Stream is 'materialized' by `forEach` call above so we have to do clean up after that
    // Otherwise we would keep entries for one extra iteration
    histogram.entrySet().removeIf(e -> e.getValue().get() == 0L);
  }

  // important that this is non-final and package private; allows concurrency tests
  void emitEvents(Stream<Pair<String, Long>> items) {
    items.forEach(e -> createAndCommitEvent(e.getKey(), e.getValue()));
  }

  private void createAndCommitEvent(final String type, final long count) {
    final ExceptionCountEvent event = new ExceptionCountEvent(type, count);
    if (event.shouldCommit()) {
      event.commit();
    }
  }

  static class Pair<K, V> {

    final K key;
    final V value;

    public static <K, V> Pair<K, V> of(final K key, final V value) {
      return new Pair<>(key, value);
    }

    public Pair(final K key, final V value) {
      this.key = key;
      this.value = value;
    }

    public K getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }
  }
}
