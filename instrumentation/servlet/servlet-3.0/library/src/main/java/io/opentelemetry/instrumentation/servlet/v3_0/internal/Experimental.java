/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.internal;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.internal.DeprecatedCaptureNames;
import io.opentelemetry.instrumentation.servlet.v3_0.ServletTelemetryBuilder;
import java.util.Collection;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<ServletTelemetryBuilder, Boolean> setEmitExperimentalTelemetry;

  @Nullable
  private static volatile BiConsumer<ServletTelemetryBuilder, Boolean>
      setAddTraceIdRequestAttribute;

  @Nullable
  private static volatile BiConsumer<ServletTelemetryBuilder, Boolean> setCaptureEnduserId;

  @Nullable
  private static volatile BiConsumer<ServletTelemetryBuilder, IncludeExclude> setRequestParameters;

  /**
   * Sets whether experimental HTTP telemetry should be emitted.
   *
   * @param builder the telemetry builder
   * @param emitExperimentalTelemetry {@code true} to emit experimental telemetry
   */
  public static void setEmitExperimentalTelemetry(
      ServletTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalTelemetry != null) {
      setEmitExperimentalTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  /**
   * Sets whether to add {@code trace_id} and {@code span_id} as a request attribute.
   *
   * @param builder the telemetry builder
   * @param traceIdRequestAttributeEnabled {@code true} to add trace ID and span ID as request
   *     attributes
   * @see javax.servlet.ServletRequest#setAttribute(String, Object)
   */
  public static void setTraceIdRequestAttributeEnabled(
      ServletTelemetryBuilder builder, boolean traceIdRequestAttributeEnabled) {
    if (setAddTraceIdRequestAttribute != null) {
      setAddTraceIdRequestAttribute.accept(builder, traceIdRequestAttributeEnabled);
    }
  }

  /**
   * Sets whether to capture the {@code enduser.id} span attribute.
   *
   * @param builder the telemetry builder
   * @param captureEnduserId {@code true} to capture {@code enduser.id}
   */
  public static void setCaptureEnduserId(
      ServletTelemetryBuilder builder, boolean captureEnduserId) {
    if (setCaptureEnduserId != null) {
      setCaptureEnduserId.accept(builder, captureEnduserId);
    }
  }

  /**
   * Sets which request parameters should be captured as span attributes.
   *
   * <p>Request parameters will be captured as attributes with the format {@code
   * servlet.request.parameter.<name>}.
   *
   * <p>Parameter names and selector patterns are matched case-sensitively. {@code ?} matches any
   * single character and {@code *} matches any number of characters, including none. Excluded
   * patterns take precedence over included patterns. An absent or empty selector captures no
   * parameters; a selector with only excluded patterns captures every parameter that it does not
   * exclude.
   *
   * @param builder the telemetry builder
   * @param requestParameters request parameter selector
   * @see javax.servlet.ServletRequest#getParameterNames()
   * @see javax.servlet.ServletRequest#getParameterValues(String)
   */
  public static void setRequestParameters(
      ServletTelemetryBuilder builder, IncludeExclude requestParameters) {
    if (setRequestParameters != null) {
      setRequestParameters.accept(builder, requestParameters);
    }
  }

  /**
   * Sets the request parameters to be captured as span attributes.
   *
   * <p>The parameter names are matched literally. Names containing {@code *} or {@code ?} are
   * ignored and logged, since this setting never supported wildcards.
   *
   * @param builder the telemetry builder
   * @param captureRequestParameters request parameter names to capture
   * @deprecated Use {@link #setRequestParameters(ServletTelemetryBuilder, IncludeExclude)} instead.
   *     May be removed in the next minor release.
   * @see javax.servlet.ServletRequest#getParameterValues(String)
   */
  @Deprecated // may be removed in the next minor release
  public static void setCaptureRequestParameters(
      ServletTelemetryBuilder builder, Collection<String> captureRequestParameters) {
    setRequestParameters(
        builder,
        DeprecatedCaptureNames.toSelectorOrEmpty(
            captureRequestParameters,
            "Experimental.setCaptureRequestParameters()",
            "setRequestParameters(ServletTelemetryBuilder, IncludeExclude)"));
  }

  public static void internalSetEmitExperimentalTelemetry(
      BiConsumer<ServletTelemetryBuilder, Boolean> setEmitExperimentalTelemetry) {
    Experimental.setEmitExperimentalTelemetry = setEmitExperimentalTelemetry;
  }

  public static void internalSetAddTraceIdRequestAttribute(
      BiConsumer<ServletTelemetryBuilder, Boolean> setAddTraceIdRequestAttribute) {
    Experimental.setAddTraceIdRequestAttribute = setAddTraceIdRequestAttribute;
  }

  public static void internalSetCaptureEnduserId(
      BiConsumer<ServletTelemetryBuilder, Boolean> setCaptureEnduserId) {
    Experimental.setCaptureEnduserId = setCaptureEnduserId;
  }

  public static void internalSetRequestParameters(
      BiConsumer<ServletTelemetryBuilder, IncludeExclude> setRequestParameters) {
    Experimental.setRequestParameters = setRequestParameters;
  }

  private Experimental() {}
}
