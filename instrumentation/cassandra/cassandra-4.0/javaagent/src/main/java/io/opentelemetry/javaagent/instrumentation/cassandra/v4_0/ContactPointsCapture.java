/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.metadata.EndPoint;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

public class ContactPointsCapture {

  private static final ThreadLocal<Deque<ContactPointsCapture>> current = new ThreadLocal<>();

  @Nullable private List<String> configuredContactPoints;
  @Nullable private Set<EndPoint> programmaticContactPoints;
  private boolean invalid;

  public static ContactPointsCapture start() {
    ContactPointsCapture capture = new ContactPointsCapture();
    Deque<ContactPointsCapture> captures = current.get();
    if (captures == null) {
      captures = new ArrayDeque<>();
      current.set(captures);
    }
    captures.push(capture);
    return capture;
  }

  public static void capture(
      Set<EndPoint> programmaticContactPoints, List<String> configuredContactPoints) {
    Deque<ContactPointsCapture> captures = current.get();
    if (captures == null) {
      return;
    }
    ContactPointsCapture capture = captures.peek();
    if (capture == null) {
      return;
    }
    if (capture.configuredContactPoints != null) {
      capture.invalid = true;
      return;
    }
    capture.programmaticContactPoints = new LinkedHashSet<>(programmaticContactPoints);
    capture.configuredContactPoints = new ArrayList<>(configuredContactPoints);
  }

  public void end() {
    Deque<ContactPointsCapture> captures = current.get();
    if (captures == null || captures.poll() != this) {
      invalid = true;
      if (captures != null) {
        captures.clear();
      }
    }
    if (captures == null || captures.isEmpty()) {
      current.remove();
    }
  }

  @Nullable
  public List<String> getConfiguredContactPoints() {
    return invalid ? null : configuredContactPoints;
  }

  @Nullable
  public Set<EndPoint> getProgrammaticContactPoints() {
    return invalid ? null : programmaticContactPoints;
  }
}
