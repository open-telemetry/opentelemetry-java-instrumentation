/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private boolean messagingReceiveInstrumentationEnabled = false;
  private List<String> capturedHeaders = emptyList();

  NatsTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setMessagingReceiveInstrumentationEnabled(boolean enabled) {
    this.messagingReceiveInstrumentationEnabled = enabled;
    return this;
  }

  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
    return this;
  }

  public NatsTelemetry build() {
    return new NatsTelemetry(
        NatsInstrumenterFactory.createProducerInstrumenter(openTelemetry, capturedHeaders),
        NatsInstrumenterFactory.createConsumerReceiveInstrumenter(
            openTelemetry, messagingReceiveInstrumentationEnabled, capturedHeaders),
        NatsInstrumenterFactory.createConsumerProcessInstrumenter(
            openTelemetry, messagingReceiveInstrumentationEnabled, capturedHeaders));
  }
}
