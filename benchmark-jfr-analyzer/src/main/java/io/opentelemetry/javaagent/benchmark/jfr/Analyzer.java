/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.jfr;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordingFile;

@SuppressWarnings("SystemOut")
public class Analyzer {

  private static final Node syntheticRootNode = new Node("");
  private static int totalSamples = 0;

  public static void main(String[] args) throws Exception {
    File jfrFile = new File(args[0]);
    List<RecordedEvent> events =
        RecordingFile.readAllEvents(jfrFile.toPath()).stream()
            .filter(e -> e.getEventType().getName().equals("jdk.ExecutionSample"))
            .collect(Collectors.toList());

    Set<String> agentCallers = getAgentCallers(events);

    for (RecordedEvent event : events) {
      totalSamples++;
      processStackTrace(event.getStackTrace(), agentCallers);
    }

    int totalAgentSamples = 0;
    for (Node rootNode : syntheticRootNode.getOrderedChildNodes()) {
      totalAgentSamples += rootNode.count;
    }

    System.out.println("Total samples: " + totalSamples);
    System.out.print("Total agent samples: " + totalAgentSamples);
    System.out.format(" (%.2f%%)%n", 100 * totalAgentSamples / (double) totalSamples);
    System.out.println();
    for (Node rootNode : syntheticRootNode.getOrderedChildNodes()) {
      printNode(rootNode, 0);
    }
  }

  // getting direct callers since those are likely the instrumented methods
  private static Set<String> getAgentCallers(List<RecordedEvent> events) {
    return events.stream()
        .map(e -> getAgentCaller(e.getStackTrace()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  @Nullable
  private static String getAgentCaller(RecordedStackTrace stackTrace) {
    List<RecordedFrame> frames = stackTrace.getFrames();
    for (int i = frames.size() - 1; i >= 0; i--) {
      RecordedFrame frame = frames.get(i);
      RecordedMethod method = frame.getMethod();
      if (isAgentMethod(method)) {
        RecordedFrame callerFrame = frames.get(i + 1);
        RecordedMethod callerMethod = callerFrame.getMethod();
        return getStackTraceElement(callerMethod, callerFrame);
      }
    }
    return null;
  }

  private static void printNode(Node node, int indent) {
    for (int i = 0; i < indent; i++) {
      System.out.print("  ");
    }
    System.out.format("%3d %s%n", node.count, node.frame);
    for (Node childNode : node.getOrderedChildNodes()) {
      printNode(childNode, indent + 1);
    }
  }

  private static void processStackTrace(RecordedStackTrace stackTrace, Set<String> agentCallers) {
    boolean analyze = false;
    int analyzeFromIndex = 0;
    List<RecordedFrame> frames = stackTrace.getFrames();
    for (int i = frames.size() - 1; i >= 0; i--) {
      RecordedFrame frame = frames.get(i);
      RecordedMethod method = frame.getMethod();
      String stackTraceElement = getStackTraceElement(method, frame);
      if (agentCallers.contains(stackTraceElement)) {
        if (i == 0) {
          analyze = true;
          analyzeFromIndex = i;
          break;
        }
        RecordedMethod nextMethod = frames.get(i - 1).getMethod();
        String nextClassName = nextMethod.getType().getName();
        // calls to java.* inside of the agent caller (likely an instrumented method) are
        // potentially part of the injected agent code
        if (nextClassName.startsWith("java.") || isAgentMethod(nextMethod)) {
          analyze = true;
          analyzeFromIndex = Math.min(i + 2, frames.size() - 1);
          break;
        }
      }
      if (isAgentMethod(method)) {
        analyze = true;
        analyzeFromIndex = Math.min(i + 1, frames.size() - 1);
        break;
      }
    }
    if (!analyze) {
      return;
    }
    Node node = syntheticRootNode;
    for (int i = analyzeFromIndex; i >= 0; i--) {
      RecordedFrame frame = frames.get(i);
      RecordedMethod method = frame.getMethod();
      String stackTraceElement = getStackTraceElement(method, frame);
      node = node.recordChildSample(stackTraceElement);
    }
  }

  private static boolean isAgentMethod(RecordedMethod method) {
    String className = method.getType().getName();
    String methodName = method.getName();
    return className.startsWith("io.opentelemetry.javaagent.")
        && !className.startsWith("io.opentelemetry.javaagent.benchmark.")
        // this shows up in stack traces because it's part of the filter chain
        && !(className.equals(
                "io.opentelemetry.javaagent.instrumentation.springwebmvc.HandlerMappingResourceNameFilter")
            && methodName.equals("doFilter"));
  }

  private static String getStackTraceElement(RecordedMethod method, RecordedFrame frame) {
    return method.getType().getName()
        + "."
        + method.getName()
        + "() line: "
        + frame.getLineNumber();
  }

  private static class Node {

    private final String frame;
    private final Map<String, Node> childNodes = new HashMap<>();
    private int count;

    private Node(String frame) {
      this.frame = frame;
    }

    private Node recordChildSample(String stackTraceElement) {
      Node childNode = childNodes.get(stackTraceElement);
      if (childNode == null) {
        childNode = new Node(stackTraceElement);
        childNodes.put(stackTraceElement, childNode);
      }
      childNode.count++;
      return childNode;
    }

    private List<Node> getOrderedChildNodes() {
      return childNodes.values().stream()
          .sorted(Comparator.comparingInt(Node::getCount).reversed())
          .collect(Collectors.toList());
    }

    private int getCount() {
      return count;
    }
  }

  private Analyzer() {}
}
