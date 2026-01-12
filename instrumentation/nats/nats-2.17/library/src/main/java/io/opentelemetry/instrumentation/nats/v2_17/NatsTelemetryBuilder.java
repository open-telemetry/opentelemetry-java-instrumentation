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

/** A builder of {@link NatsTelemetry}. */
public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private List<String> capturedHeaders = emptyList();

  NatsTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * @param capturedHeaders A list of messaging header names.
   */
  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
    return this;
  }

  /** Returns a new {@link NatsTelemetry} with the settings of this {@link NatsTelemetryBuilder}. */
  public NatsTelemetry build() {
    return new NatsTelemetry(
        NatsInstrumenterFactory.createProducerInstrumenter(openTelemetry, capturedHeaders),
        NatsInstrumenterFactory.createConsumerProcessInstrumenter(openTelemetry, capturedHeaders));
  }
}
