/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class TelemetryDataUtil {

  public static Comparator<List<SpanData>> orderByRootSpanKind(SpanKind... spanKinds) {
    List<SpanKind> list = Arrays.asList(spanKinds);
    return Comparator.comparing(span -> list.indexOf(span.get(0).getKind()));
  }

  public static Comparator<List<SpanData>> orderByRootSpanName(String... names) {
    List<String> list = Arrays.asList(names);
    return Comparator.comparing(span -> list.indexOf(span.get(0).getName()));
  }

  public static List<List<SpanData>> groupTraces(List<SpanData> spans) {
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

  public static List<List<SpanData>> waitForTraces(Supplier<List<SpanData>> supplier, int number)
      throws InterruptedException, TimeoutException {
    return waitForTraces(supplier, number, 20, TimeUnit.SECONDS);
  }

  public static List<List<SpanData>> waitForTraces(
      Supplier<List<SpanData>> supplier, int number, long timeout, TimeUnit unit)
      throws InterruptedException, TimeoutException {
    long startTime = System.nanoTime();
    List<List<SpanData>> allTraces = groupTraces(supplier.get());
    List<List<SpanData>> completeTraces =
        allTraces.stream().filter(TelemetryDataUtil::isCompleted).collect(toList());
    while (completeTraces.size() < number && elapsedSeconds(startTime) < unit.toSeconds(timeout)) {
      allTraces = groupTraces(supplier.get());
      completeTraces = allTraces.stream().filter(TelemetryDataUtil::isCompleted).collect(toList());
      Thread.sleep(10);
    }
    if (completeTraces.size() < number) {
      throw new TimeoutException(
          "Timeout waiting for "
              + number
              + " completed trace(s), found "
              + completeTraces.size()
              + " completed trace(s) and "
              + allTraces.size()
              + " total trace(s): "
              + allTraces);
    }
    return completeTraces;
  }

  private static long elapsedSeconds(long startTime) {
    return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);
  }

  // must be called under tracesLock
  private static void sortTraces(List<List<SpanData>> traces) {
    traces.sort(Comparator.comparingLong(TelemetryDataUtil::getMinSpanOrder));
  }

  private static long getMinSpanOrder(List<SpanData> spans) {
    return spans.stream().mapToLong(SpanData::getStartEpochNanos).min().orElse(0);
  }

  @SuppressWarnings("UnstableApiUsage")
  private static List<SpanData> sort(List<SpanData> trace) {

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

    List<Node> orderedNodes = new ArrayList<>();
    for (Node rootNode : rootNodes) {
      traversePreOrder(rootNode, orderedNodes);
    }

    List<SpanData> orderedSpans = new ArrayList<>();
    for (Node node : orderedNodes) {
      orderedSpans.add(node.span);
    }
    return orderedSpans;
  }

  private static void sortOneLevel(List<Node> nodes) {
    nodes.sort(Comparator.comparingLong(node -> node.span.getStartEpochNanos()));
  }

  private static void traversePreOrder(Node node, List<Node> accumulator) {
    accumulator.add(node);
    for (Node child : node.childNodes) {
      traversePreOrder(child, accumulator);
    }
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

  private TelemetryDataUtil() {}
}
