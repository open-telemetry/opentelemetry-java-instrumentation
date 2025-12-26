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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** A builder of {@link NatsTelemetry}. */
public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private List<String> capturedHeaders = emptyList();
  private List<String> temporaryPrefixes = DEFAULT_TEMPORARY_PREFIXES;

  /**
   * _INBOX. is the prefix used in the NATS Java client for request/reply. Usually one-off
   * subscription is used for each request. _R_. is the prefix used in NodeJS environments for
   * request/reply. There is only one shared subscription per connection for all requests.
   */
  public static final List<String> DEFAULT_TEMPORARY_PREFIXES = Arrays.asList("_INBOX.", "_R_.");

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

  /**
   * Configures the prefixes used for temporary subjects.
   *
   * @param temporaryPrefixes A list of prefixes.
   */
  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setTemporaryPrefixes(Collection<String> temporaryPrefixes) {
    this.temporaryPrefixes = new ArrayList<>(temporaryPrefixes);
    return this;
  }

  /** Returns a new {@link NatsTelemetry} with the settings of this {@link NatsTelemetryBuilder}. */
  public NatsTelemetry build() {
    return new NatsTelemetry(
        NatsInstrumenterFactory.createProducerInstrumenter(
            openTelemetry, capturedHeaders, temporaryPrefixes),
        NatsInstrumenterFactory.createConsumerProcessInstrumenter(
            openTelemetry, capturedHeaders, temporaryPrefixes));
  }
}
