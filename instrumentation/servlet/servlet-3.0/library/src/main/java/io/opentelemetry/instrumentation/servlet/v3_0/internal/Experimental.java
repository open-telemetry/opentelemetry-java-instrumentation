/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.v3_0.internal;

import io.opentelemetry.instrumentation.servlet.v3_0.ServletTelemetryBuilder;
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

  public static void setEmitExperimentalTelemetry(
      ServletTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalTelemetry != null) {
      setEmitExperimentalTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void addTraceIdRequestAttribute(
      ServletTelemetryBuilder builder, boolean addTraceIdRequestAttribute) {
    if (setAddTraceIdRequestAttribute != null) {
      setAddTraceIdRequestAttribute.accept(builder, addTraceIdRequestAttribute);
    }
  }

  public static void setCaptureEnduserId(
      ServletTelemetryBuilder builder, boolean captureEnduserId) {
    if (setCaptureEnduserId != null) {
      setCaptureEnduserId.accept(builder, captureEnduserId);
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

  private Experimental() {}
}
