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

/** A builder of {@link NatsTelemetry}. */
public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private Collection<String> headers = emptyList();

  NatsTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key. The
   * {@code <name>} part in the attribute key is the header name with its original spelling,
   * including dashes.
   *
   * <p>Names are looked up literally using the messaging library's header-name case sensitivity.
   * {@code *} and {@code ?} are not wildcards. No headers are captured by default.
   */
  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.headers = new ArrayList<>(capturedHeaders);
    return this;
  }

  /** Returns a new {@link NatsTelemetry} with the settings of this {@link NatsTelemetryBuilder}. */
  public NatsTelemetry build() {
    return new NatsTelemetry(
        NatsInstrumenterFactory.createPublishInstrumenter(openTelemetry, headers),
        NatsInstrumenterFactory.createRequestInstrumenter(openTelemetry, headers),
        NatsInstrumenterFactory.createSettleInstrumenter(openTelemetry, headers),
        NatsInstrumenterFactory.createConsumerProcessInstrumenter(openTelemetry, headers));
  }
}
