/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.Collection;

/** A builder of {@link AwsSdkTelemetry}. */
public final class AwsSdkTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private IncludeExclude headers = IncludeExclude.builder().build();
  private boolean captureExperimentalSpanAttributes;
  private boolean messagingReceiveTelemetryEnabled;
  private boolean messageCreateSpansEnabled = true;

  AwsSdkTelemetryBuilder(OpenTelemetry openTelemetry) {
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
  public AwsSdkTelemetryBuilder setHeaders(IncludeExclude headers) {
    this.headers = headers;
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * @param capturedHeaders A list of messaging header names.
   * @deprecated Use {@link #setHeaders(IncludeExclude)} instead. May be removed in the next minor
   *     release.
   */
  @Deprecated // may be removed in the next minor release
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    return setHeaders(IncludeExclude.builder().setIncluded(capturedHeaders).build());
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions
   */
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Set whether to capture the consumer message receive telemetry in messaging instrumentation.
   *
   * <p>Note that this will cause the consumer side to start a new trace, with only a span link
   * connecting it to the producer trace.
   */
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setMessagingReceiveTelemetryEnabled(
      boolean messagingReceiveTelemetryEnabled) {
    this.messagingReceiveTelemetryEnabled = messagingReceiveTelemetryEnabled;
    return this;
  }

  /**
   * Sets whether a producer "Create" span is emitted for each entry in an SQS batch send.
   *
   * <p>This option only applies when the stable messaging semantic conventions are enabled. It is
   * enabled by default.
   */
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setMessageCreateSpansEnabled(boolean messageCreateSpansEnabled) {
    this.messageCreateSpansEnabled = messageCreateSpansEnabled;
    return this;
  }

  /**
   * Returns a new {@link AwsSdkTelemetry} with the settings of this {@link AwsSdkTelemetryBuilder}.
   */
  public AwsSdkTelemetry build() {
    return new AwsSdkTelemetry(
        openTelemetry,
        headers,
        captureExperimentalSpanAttributes,
        messagingReceiveTelemetryEnabled,
        messageCreateSpansEnabled);
  }
}
