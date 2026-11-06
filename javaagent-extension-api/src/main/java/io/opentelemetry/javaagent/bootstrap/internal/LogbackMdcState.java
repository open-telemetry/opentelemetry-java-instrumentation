/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

/**
 * Coordinates original-MDC reads during Java agent log export and synchronous SDK processing.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class LogbackMdcState {

  private static final ScopedThreadValue<Boolean> currentExportRead = new ScopedThreadValue<>();

  public static ScopedThreadValue<Boolean> currentExportRead() {
    return currentExportRead;
  }

  private LogbackMdcState() {}
}
