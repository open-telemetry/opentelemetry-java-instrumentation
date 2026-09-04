/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.DeprecatedCaptureNames;
import io.opentelemetry.instrumentation.nats.v2_17.internal.NatsInstrumenterFactory;
import java.util.Collection;

/** A builder of {@link NatsTelemetry}. */
public final class NatsTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private IncludeExclude headers = IncludeExclude.builder().build();

  NatsTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Configures which message headers are captured as span attributes.
   *
   * <p>Header values are captured under the {@code messaging.header.<name>} attribute key. The
   * {@code <name>} part in the attribute key is the header name with dashes replaced by underscores
   * unless {@code otel.instrumentation.common.v3-preview} is enabled, in which case dashes are
   * preserved.
   *
   * <p>Matching is case-sensitive. {@code ?} matches one character and {@code *} matches any number
   * of characters, including none. Excluded patterns take precedence over included patterns. A
   * selector with no included patterns captures every header that is not excluded, and an
   * {@linkplain IncludeExclude#isEmpty() empty} selector captures no headers.
   */
  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setHeaders(IncludeExclude headers) {
    this.headers = headers;
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * <p>The header names are matched literally. Names containing {@code *} or {@code ?} are ignored
   * and logged, since this setting never supported wildcards.
   *
   * @param capturedHeaders A list of messaging header names.
   * @deprecated Use {@link #setHeaders(IncludeExclude)} instead. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public NatsTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    return setHeaders(
        DeprecatedCaptureNames.toSelectorOrEmpty(
            capturedHeaders,
            "NatsTelemetryBuilder.setCapturedHeaders()",
            "setHeaders(IncludeExclude)"));
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
