/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logback;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

/**
 * Coordinates original-MDC reads during Java agent log export.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LogbackMdcState {

  private static final ScopedThreadValue<Object> suppressMdcEnrichment = new ScopedThreadValue<>();

  public static ScopedThreadValue<Object> suppressMdcEnrichment() {
    return suppressMdcEnrichment;
  }

  private LogbackMdcState() {}
}
