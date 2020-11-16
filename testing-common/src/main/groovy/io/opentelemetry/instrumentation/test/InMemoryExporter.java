/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.TreeTraverser;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import io.opentelemetry.api.common.AttributeConsumer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemoryExporter implements SpanProcessor {

  private static final Logger log = LoggerFactory.getLogger(InMemoryExporter.class);

  // not using span startEpochNanos since that is not strictly increasing so can lead to ties
  private final Map<String, Integer> spanOrders = new ConcurrentHashMap<>();
  private final AtomicInteger nextSpanOrder = new AtomicInteger();

  private volatile boolean forceFlushCalled;

  @Override
  public void onStart(Context context, ReadWriteSpan readWriteSpan) {
    SpanData sd = readWriteSpan.toSpanData();
    log.debug(
        ">>>{} SPAN START: {} id={} traceid={} parent={}, library={}",
        sd.getStartEpochNanos(),
        sd.getName(),
        sd.getSpanId(),
        sd.getTraceId(),
        sd.getParentSpanId(),
        sd.getInstrumentationLibraryInfo());
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan readableSpan) {}

  private String printSpanAttributes(SpanData sd) {
    final StringBuilder attributes = new StringBuilder();
    sd.getAttributes()
        .forEach(
            new AttributeConsumer() {
              @Override
              public <T> void accept(AttributeKey<T> key, T value) {
                attributes.append(String.format("Attribute %s=%s", key, value));
              }
            });
    return attributes.toString();
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  public List<List<SpanData>> getTraces() {
    List<SpanData> spans = AgentTestingExporterAccess.getExportedSpans();
    List<List<SpanData>> traces =
        new ArrayList<>(
            spans.stream().collect(Collectors.groupingBy(SpanData::getTraceId)).values());
    sortTraces(traces);
    for (int i = 0; i < traces.size(); i++) {
      List<SpanData> trace = traces.get(i);
      traces.set(i, sort(trace));
    }
    return traces;
  }

  public void waitForTraces(int number) throws InterruptedException, TimeoutException {
    waitForTraces(number, Predicates.<List<SpanData>>alwaysFalse());
  }

  public List<List<SpanData>> waitForTraces(int number, Predicate<List<SpanData>> excludes)
      throws InterruptedException, TimeoutException {
    long remainingWaitMillis = TimeUnit.SECONDS.toMillis(20);
    List<List<SpanData>> traces = getCompletedAndFilteredTraces(excludes);
    while (traces.size() < number && remainingWaitMillis > 0) {
      Stopwatch stopwatch = Stopwatch.createStarted();
      Thread.sleep(remainingWaitMillis);
      remainingWaitMillis -= stopwatch.elapsed(TimeUnit.MILLISECONDS);
      traces = getCompletedAndFilteredTraces(excludes);
    }
    if (traces.size() < number) {
      throw new TimeoutException(
          "Timeout waiting for "
              + number
              + " completed/filtered trace(s), found "
              + traces.size()
              + " completed/filtered trace(s) and "
              + traces.size()
              + " total trace(s): "
              + traces);
    }
    return traces;
  }

  private List<List<SpanData>> getCompletedAndFilteredTraces(Predicate<List<SpanData>> excludes) {
    List<List<SpanData>> traces = new ArrayList<>();
    for (List<SpanData> trace : getTraces()) {
      if (isCompleted(trace) && !excludes.apply(trace)) {
        traces.add(trace);
      }
    }
    return traces;
  }

  public void clear() {}

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode forceFlush() {
    forceFlushCalled = true;
    return CompletableResultCode.ofSuccess();
  }

  public boolean forceFlushCalled() {
    return forceFlushCalled;
  }

  // must be called under tracesLock
  private void sortTraces(List<List<SpanData>> traces) {
    Collections.sort(
        traces,
        new Comparator<List<SpanData>>() {
          @Override
          public int compare(List<SpanData> trace1, List<SpanData> trace2) {
            return Longs.compare(getMinSpanOrder(trace1), getMinSpanOrder(trace2));
          }
        });
  }

  private long getMinSpanOrder(List<SpanData> spans) {
    long min = Long.MAX_VALUE;
    for (SpanData span : spans) {
      min = Math.min(min, getSpanOrder(span));
    }
    return min;
  }

  private List<SpanData> sort(List<SpanData> trace) {

    Map<String, Node> lookup = new HashMap<>();
    for (SpanData span : trace) {
      lookup.put(span.getSpanId(), new Node(span));
    }

    for (Node node : lookup.values()) {
      String parentSpanId = node.span.getParentSpanId();
      if (SpanId.isValid(parentSpanId)) {
        Node parentNode = lookup.get(parentSpanId);
        if (parentNode != null) {
          parentNode.childNodes.add(node);
          node.root = false;
        }
      }
    }

    List<Node> rootNodes = new ArrayList<>();
    for (Node node : lookup.values()) {
      sortOneLevel(node.childNodes);
      if (node.root) {
        rootNodes.add(node);
      }
    }
    sortOneLevel(rootNodes);

    TreeTraverser<Node> traverser =
        new TreeTraverser<Node>() {
          @Override
          public Iterable<Node> children(Node node) {
            return node.childNodes;
          }
        };

    List<Node> orderedNodes = new ArrayList<>();
    for (Node rootNode : rootNodes) {
      Iterables.addAll(orderedNodes, traverser.preOrderTraversal(rootNode));
    }

    List<SpanData> orderedSpans = new ArrayList<>();
    for (Node node : orderedNodes) {
      orderedSpans.add(node.span);
    }
    return orderedSpans;
  }

  private void sortOneLevel(List<Node> nodes) {
    Collections.sort(
        nodes,
        new Comparator<Node>() {
          @Override
          public int compare(Node node1, Node node2) {
            return Ints.compare(getSpanOrder(node1.span), getSpanOrder(node2.span));
          }
        });
  }

  private int getSpanOrder(SpanData span) {
    Integer order = spanOrders.get(span.getSpanId());
    if (order == null) {
      throw new IllegalStateException("order not found for span: " + span);
    }
    return order;
  }

  // trace is completed if root span is present
  private static boolean isCompleted(List<SpanData> trace) {
    for (SpanData span : trace) {
      if (!SpanId.isValid(span.getParentSpanId())) {
        return true;
      }
      if (span.getParentSpanId().equals("0000000000000456")) {
        // this is a special parent id that some tests use
        return true;
      }
    }
    return false;
  }

  private static class Node {

    private final SpanData span;
    private final List<Node> childNodes = new ArrayList<>();
    private boolean root = true;

    private Node(SpanData span) {
      this.span = span;
    }
  }
}
