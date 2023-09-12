/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link AwsSdkTelemetry}. */
public final class AwsSdkTelemetryBuilder {

  private final OpenTelemetry openTelemetry;

  private boolean captureExperimentalSpanAttributes;

  private boolean useMessagingPropagator;

  private boolean recordIndividualHttpError;

  private boolean useXrayPropagator = true;

  AwsSdkTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
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
   * Sets whether the {@link io.opentelemetry.context.propagation.TextMapPropagator} configured in
   * the provided {@link OpenTelemetry} should be used to inject into supported messaging attributes
   * (currently only SQS; SNS may follow).
   *
   * <p>In addition, the X-Ray propagator is always used.
   *
   * <p>Using the messaging propagator is needed if your tracing vendor requires special tracestate
   * entries or legacy propagation information that cannot be transported via X-Ray headers. It may
   * also be useful if you need to directly connect spans over messaging in your tracing backend,
   * bypassing any intermediate spans/X-Ray segments that AWS may create in the delivery process.
   *
   * <p>This option is off by default. If enabled, on extraction the configured propagator will be
   * preferred over X-Ray if it can extract anything.
   */
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setUseConfiguredPropagatorForMessaging(
      boolean useMessagingPropagator) {
    this.useMessagingPropagator = useMessagingPropagator;
    return this;
  }

  /**
   * Sets whether errors returned by each individual HTTP request should be recorded as events for
   * the SDK span.
   *
   * <p>This option is off by default. If enabled, the HTTP error code and the error message will be
   * captured and associated with the span. This provides detailed insights into errors on a
   * per-request basis.
   */
  @CanIgnoreReturnValue
  public AwsSdkTelemetryBuilder setRecordIndividualHttpError(boolean recordIndividualHttpError) {
    this.recordIndividualHttpError = recordIndividualHttpError;
    return this;
  }

  /**
   * This setter implemented package-private for testing the messaging propagator, it does not seem
   * too useful in general. The option is on by default.
   *
   * <p>If this needs to be exposed for non-testing use cases, consider if you need to refine this
   * feature so that it disable this only for requests supported by {@link
   * #setUseConfiguredPropagatorForMessaging(boolean)}
   */
  @CanIgnoreReturnValue
  AwsSdkTelemetryBuilder setUseXrayPropagator(boolean useMessagingPropagator) {
    this.useXrayPropagator = useMessagingPropagator;
    return this;
  }

  /**
   * Returns a new {@link AwsSdkTelemetry} with the settings of this {@link AwsSdkTelemetryBuilder}.
   */
  public AwsSdkTelemetry build() {
    return new AwsSdkTelemetry(
        openTelemetry,
        captureExperimentalSpanAttributes,
        useMessagingPropagator,
        useXrayPropagator,
        recordIndividualHttpError);
  }
}
