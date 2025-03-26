/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxClientTelemetryBuilder;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.SpringWebfluxServerTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<SpringWebfluxClientTelemetryBuilder, Boolean>
      setEmitExperimentalClientTelemetry;

  @Nullable
  private static volatile BiConsumer<SpringWebfluxServerTelemetryBuilder, Boolean>
      setEmitExperimentalServerTelemetry;

  public static void setEmitExperimentalTelemetry(
      SpringWebfluxClientTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalClientTelemetry != null) {
      setEmitExperimentalClientTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void setEmitExperimentalTelemetry(
      SpringWebfluxServerTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalServerTelemetry != null) {
      setEmitExperimentalServerTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void internalSetEmitExperimentalClientTelemetry(
      BiConsumer<SpringWebfluxClientTelemetryBuilder, Boolean> setEmitExperimentalClientTelemetry) {
    Experimental.setEmitExperimentalClientTelemetry = setEmitExperimentalClientTelemetry;
  }

  public static void internalSetEmitExperimentalServerTelemetry(
      BiConsumer<SpringWebfluxServerTelemetryBuilder, Boolean> setEmitExperimentalServerTelemetry) {
    Experimental.setEmitExperimentalServerTelemetry = setEmitExperimentalServerTelemetry;
  }

  private Experimental() {}
}
