/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import java.util.ArrayList;
import java.util.Collection;

/** A builder of {@link AwsSdkTelemetry}. */
public final class AwsSdkTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private Collection<String> headers = emptyList();
  private boolean captureExperimentalSpanAttributes;
  private boolean messagingReceiveTelemetryEnabled;
  private boolean batchSendMessageCreationSpansEnabled = true;

  AwsSdkTelemetryBuilder(OpenTelemetry openTelemetry) {
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
  public AwsSdkTelemetryBuilder setCapturedHeaders(Collection<String> capturedHeaders) {
    this.headers = new ArrayList<>(capturedHeaders);
    return this;
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
   * Sets whether a producer "Create" span is emitted for each eligible entry in an SQS batch send.
   * An entry is eligible when it does not already contain a creation context and the AWS SDK
   * version supports the per-entry {@code AWSTraceHeader} system attribute.
   *
   * <p>This option is enabled by default.
   */
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setBatchSendMessageCreationSpansEnabled(
      boolean batchSendMessageCreationSpansEnabled) {
    this.batchSendMessageCreationSpansEnabled = batchSendMessageCreationSpansEnabled;
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
        batchSendMessageCreationSpansEnabled);
  }
}
