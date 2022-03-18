/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import java.util.Locale;

/**
 * Represents type of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#operation-names">operations</a>
 * that may be used in a messaging system.
 */
public enum MessageOperation {
  SEND,
  RECEIVE,
  PROCESS;

  /**
   * Returns the operation name as defined in <a
   * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#operation-names">the
   * specification</a>.
   *
   * @deprecated This method is going to be made non-public in the next release.
   */
  @Deprecated
  public String operationName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
