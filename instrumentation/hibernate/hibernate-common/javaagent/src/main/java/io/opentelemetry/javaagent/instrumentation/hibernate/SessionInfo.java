/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import static io.opentelemetry.javaagent.instrumentation.hibernate.HibernateSingletons.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;

import java.util.UUID;

public class SessionInfo {
  private final String sessionId;

  public SessionInfo() {
    sessionId = generateSessionId();
  }

  public String getSessionId() {
    return sessionId;
  }

  private static String generateSessionId() {
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return null;
    }

    return UUID.randomUUID().toString();
  }
}
