/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.nats.v2_21.internal.NatsInstrumenterFactory;

public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  NatsTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  public NatsTelemetry build() {
    return new NatsTelemetry(NatsInstrumenterFactory.createProducerInstrumenter(openTelemetry));
  }
}
