package datadog.trace.agent.test;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.TreeTraverser;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.trace.SpanId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class ListWriter extends CopyOnWriteArrayList<List<SpanData>> implements SpanProcessor {

  // not using span startEpochNanos since that is not strictly increasing so can lead to ties
  private final Map<SpanId, Integer> spanOrders = new ConcurrentHashMap<>();
  private final AtomicInteger nextSpanOrder = new AtomicInteger();
  private final Object lock = new Object();

  @Override
  public void onStart(final ReadableSpan readableSpan) {
    spanOrders.put(readableSpan.getSpanContext().getSpanId(), nextSpanOrder.getAndIncrement());
  }

  @Override
  public void onEnd(final ReadableSpan readableSpan) {
    final SpanData span = readableSpan.toSpanData();
    synchronized (lock) {
      boolean found = false;
      for (final List<SpanData> trace : this) {
        if (trace.get(0).getTraceId().equals(span.getTraceId())) {
          trace.add(span);
          found = true;
          break;
        }
      }
      if (!found) {
        final List<SpanData> trace = new CopyOnWriteArrayList<>();
        trace.add(span);
        add(trace);
      }
      sort();
      lock.notifyAll();
    }
  }

  public void waitForTraces(final int number) throws InterruptedException, TimeoutException {
    synchronized (lock) {
      long remainingWaitMillis = TimeUnit.SECONDS.toMillis(20);
      while (completedTraceCount() < number && remainingWaitMillis > 0) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        lock.wait(remainingWaitMillis);
        remainingWaitMillis -= stopwatch.elapsed(TimeUnit.MILLISECONDS);
      }
      final int completedTraceCount = completedTraceCount();
      if (completedTraceCount < number) {
        throw new TimeoutException(
            "Timeout waiting for "
                + number
                + " completed trace(s), found "
                + completedTraceCount
                + " completed trace(s) and "
                + size()
                + " total trace(s)");
      }
    }
  }

  @Override
  public void clear() {
    super.clear();
    spanOrders.clear();
  }

  @Override
  public void shutdown() {}

  private int completedTraceCount() {
    int count = 0;
    for (final List<SpanData> trace : this) {
      if (isCompleted(trace)) {
        count++;
      }
    }
    return count;
  }

  // trace is completed if root span is present
  private boolean isCompleted(final List<SpanData> trace) {
    for (final SpanData span : trace) {
      if (!span.getParentSpanId().isValid()) {
        return true;
      }
      if (span.getParentSpanId().toLowerBase16().equals("0000000000000456")) {
        // this is a special parent id that some tests use
        return true;
      }
    }
    return false;
  }

  private void sort() {
    sortTraces();
    for (final List<SpanData> trace : this) {
      sort(trace);
    }
  }

  private void sortTraces() {
    // cannot sort CopyOnWriteArrayList in Java 7 (leads to UnsupportedOperationException)
    final List<List<SpanData>> copy = new ArrayList<>(this);
    Collections.sort(
        copy,
        new Comparator<List<SpanData>>() {
          @Override
          public int compare(final List<SpanData> trace1, final List<SpanData> trace2) {
            return Longs.compare(getMinSpanOrder(trace1), getMinSpanOrder(trace2));
          }
        });
    super.clear(); // explicitly calling super in order to bypass clearing spanOrders
    addAll(copy);
  }

  private long getMinSpanOrder(final List<SpanData> spans) {
    long min = Long.MAX_VALUE;
    for (final SpanData span : spans) {
      min = Math.min(min, getSpanOrder(span));
    }
    return min;
  }

  private void sort(final List<SpanData> trace) {

    final Map<SpanId, Node> lookup = new HashMap<>();
    for (final SpanData span : trace) {
      lookup.put(span.getSpanId(), new Node(span));
    }

    for (final Node node : lookup.values()) {
      if (node.span.getParentSpanId().isValid()) {
        final Node parentNode = lookup.get(node.span.getParentSpanId());
        if (parentNode != null) {
          parentNode.childNodes.add(node);
          node.root = false;
        }
      }
    }

    final List<Node> rootNodes = new ArrayList<>();
    for (final Node node : lookup.values()) {
      sortOneLevel(node.childNodes);
      if (node.root) {
        rootNodes.add(node);
      }
    }
    sortOneLevel(rootNodes);

    final TreeTraverser<Node> traverser =
        new TreeTraverser<Node>() {
          @Override
          public Iterable<Node> children(final Node node) {
            return node.childNodes;
          }
        };

    final List<Node> orderedNodes = new ArrayList<>();
    for (final Node rootNode : rootNodes) {
      Iterables.addAll(orderedNodes, traverser.preOrderTraversal(rootNode));
    }

    final List<SpanData> orderedSpans = new ArrayList<>();
    for (final Node node : orderedNodes) {
      orderedSpans.add(node.span);
    }
    trace.clear();
    trace.addAll(orderedSpans);
  }

  private void sortOneLevel(final List<Node> nodes) {
    Collections.sort(
        nodes,
        new Comparator<Node>() {
          @Override
          public int compare(final Node node1, final Node node2) {
            return Ints.compare(getSpanOrder(node1.span), getSpanOrder(node2.span));
          }
        });
  }

  private int getSpanOrder(final SpanData span) {
    final Integer order = spanOrders.get(span.getSpanId());
    if (order == null) {
      throw new IllegalStateException("order not found for span: " + span);
    }
    return order;
  }

  private static class Node {

    private final SpanData span;
    private final List<Node> childNodes = new ArrayList<>();
    private boolean root = true;

    private Node(final SpanData span) {
      this.span = span;
    }
  }
}
