/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsInstrumenterFactory;

public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private boolean messagingReceiveInstrumentationEnabled = false;

  NatsTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setMessagingReceiveInstrumentationEnabled(boolean enabled) {
    this.messagingReceiveInstrumentationEnabled = enabled;
    return this;
  }

  public NatsTelemetry build() {
    return new NatsTelemetry(
        NatsInstrumenterFactory.createProducerInstrumenter(openTelemetry),
        NatsInstrumenterFactory.createConsumerReceiveInstrumenter(
            openTelemetry, messagingReceiveInstrumentationEnabled),
        NatsInstrumenterFactory.createConsumerProcessInstrumenter(
            openTelemetry, messagingReceiveInstrumentationEnabled));
  }
}
