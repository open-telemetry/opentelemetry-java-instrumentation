/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v5_0.internal;

import io.opentelemetry.instrumentation.servlet.v5_0.ServletTelemetryBuilder;
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
  private static volatile BiConsumer<ServletTelemetryBuilder, Collection<String>>
      setCapturedRequestParameters;

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
   * @param addTraceIdRequestAttribute {@code true} to add trace ID and span ID as request
   *     attributes
   * @see jakarta.servlet.ServletRequest#setAttribute(String, Object)
   */
  public static void addTraceIdRequestAttribute(
      ServletTelemetryBuilder builder, boolean addTraceIdRequestAttribute) {
    if (setAddTraceIdRequestAttribute != null) {
      setAddTraceIdRequestAttribute.accept(builder, addTraceIdRequestAttribute);
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
   * Sets the request parameters to be captured as span attributes.
   *
   * <p>Request parameters will be captured as attributes with the format {@code
   * servlet.request.parameter.<name>}.
   *
   * @param builder the telemetry builder
   * @param capturedRequestParameters request parameter names to capture
   * @see jakarta.servlet.ServletRequest#getParameterValues(String)
   */
  public static void setCapturedRequestParameters(
      ServletTelemetryBuilder builder, Collection<String> capturedRequestParameters) {
    if (setCapturedRequestParameters != null) {
      setCapturedRequestParameters.accept(builder, capturedRequestParameters);
    }
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

  public static void internalSetCapturedRequestParameters(
      BiConsumer<ServletTelemetryBuilder, Collection<String>> setCapturedRequestParameters) {
    Experimental.setCapturedRequestParameters = setCapturedRequestParameters;
  }

  private Experimental() {}
}
