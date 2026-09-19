/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

import io.opentelemetry.context.Context;

final class PulsarMessageState {
  private final Context processParentContext;
  private final boolean consumedMessagesRecorded;

  PulsarMessageState(Context processParentContext, boolean consumedMessagesRecorded) {
    this.processParentContext = processParentContext;
    this.consumedMessagesRecorded = consumedMessagesRecorded;
  }

  Context processParentContext() {
    return processParentContext;
  }

  boolean wereConsumedMessagesRecorded() {
    return consumedMessagesRecorded;
  }
}
