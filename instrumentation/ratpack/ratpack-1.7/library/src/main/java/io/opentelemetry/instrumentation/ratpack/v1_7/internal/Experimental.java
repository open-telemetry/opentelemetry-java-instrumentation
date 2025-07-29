/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackClientTelemetryBuilder;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<RatpackClientTelemetryBuilder, Boolean>
      setEmitExperimentalClientTelemetry;

  @Nullable
  private static volatile BiConsumer<RatpackServerTelemetryBuilder, Boolean>
      setEmitExperimentalServerTelemetry;

  public static void setEmitExperimentalTelemetry(
      RatpackClientTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalClientTelemetry != null) {
      setEmitExperimentalClientTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void setEmitExperimentalTelemetry(
      RatpackServerTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalServerTelemetry != null) {
      setEmitExperimentalServerTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void internalSetEmitExperimentalClientTelemetry(
      BiConsumer<RatpackClientTelemetryBuilder, Boolean> setEmitExperimentalClientTelemetry) {
    Experimental.setEmitExperimentalClientTelemetry = setEmitExperimentalClientTelemetry;
  }

  public static void internalSetEmitExperimentalServerTelemetry(
      BiConsumer<RatpackServerTelemetryBuilder, Boolean> setEmitExperimentalServerTelemetry) {
    Experimental.setEmitExperimentalServerTelemetry = setEmitExperimentalServerTelemetry;
  }

  private Experimental() {}
}
