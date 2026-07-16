/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import java.util.Locale;

/**
 * Represents type of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md#operation-types">operations</a>
 * that may be used in a messaging system.
 */
public enum MessageOperation {
  PUBLISH,
  RECEIVE,
  PROCESS;

  /**
   * Returns the legacy operation name. The v1.43 operation name defaults to this value unless an
   * instrumentation supplies a system-specific override.
   */
  String operationName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
