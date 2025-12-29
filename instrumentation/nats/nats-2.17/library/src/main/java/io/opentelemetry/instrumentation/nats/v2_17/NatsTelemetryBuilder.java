/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsSubjectPattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/** A builder of {@link NatsTelemetry}. */
public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private List<String> capturedHeaders = emptyList();
  private List<Pattern> temporaryPatterns = DEFAULT_TEMPORARY_PATTERNS;

  public static final List<Pattern> DEFAULT_TEMPORARY_PATTERNS =
      Arrays.asList(NatsSubjectPattern.compile("_INBOX.*"), NatsSubjectPattern.compile("_R_.*"));

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
   * Configures the patterns used for temporary subjects.
   *
   * @param temporaryPatterns A list of patterns.
   */
  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setTemporaryPatterns(Collection<Pattern> temporaryPatterns) {
    this.temporaryPatterns = new ArrayList<>(temporaryPatterns);
    return this;
  }

  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setTemporarySubjects(Collection<String> temporarySubjects) {
    this.temporaryPatterns = new ArrayList<>();
    for (String subject : temporarySubjects) {
      this.temporaryPatterns.add(NatsSubjectPattern.compile(subject));
    }
    return this;
  }

  /** Returns a new {@link NatsTelemetry} with the settings of this {@link NatsTelemetryBuilder}. */
  public NatsTelemetry build() {
    return new NatsTelemetry(
        NatsInstrumenterFactory.createProducerInstrumenter(
            openTelemetry, capturedHeaders, temporaryPatterns),
        NatsInstrumenterFactory.createConsumerProcessInstrumenter(
            openTelemetry, capturedHeaders, temporaryPatterns));
  }
}
