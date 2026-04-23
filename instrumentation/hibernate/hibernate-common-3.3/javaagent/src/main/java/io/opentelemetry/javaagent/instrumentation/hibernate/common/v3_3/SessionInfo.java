/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate.common.v3_3;

import static io.opentelemetry.javaagent.instrumentation.hibernate.common.v3_3.HibernateInstrumenterFactory.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;

import java.util.UUID;
import javax.annotation.Nullable;

public class SessionInfo {
  @Nullable private final String sessionId;

  public SessionInfo() {
    sessionId = generateSessionId();
  }

  @Nullable
  public String getSessionId() {
    return sessionId;
  }

  @Nullable
  private static String generateSessionId() {
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      return null;
    }

    return UUID.randomUUID().toString();
  }
}
