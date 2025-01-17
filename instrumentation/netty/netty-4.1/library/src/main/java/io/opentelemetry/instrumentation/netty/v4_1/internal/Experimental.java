/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4_1.internal;

import io.opentelemetry.instrumentation.netty.v4_1.NettyClientTelemetryBuilder;
import io.opentelemetry.instrumentation.netty.v4_1.NettyServerTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<NettyClientTelemetryBuilder, Boolean>
      setEmitExperimentalClientTelemetry;

  @Nullable
  private static volatile BiConsumer<NettyServerTelemetryBuilder, Boolean>
      setEmitExperimentalServerTelemetry;

  public static void setEmitExperimentalTelemetry(
      NettyClientTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalClientTelemetry != null) {
      setEmitExperimentalClientTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void setEmitExperimentalTelemetry(
      NettyServerTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalServerTelemetry != null) {
      setEmitExperimentalServerTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void internalSetEmitExperimentalClientTelemetry(
      BiConsumer<NettyClientTelemetryBuilder, Boolean> setEmitExperimentalClientTelemetry) {
    Experimental.setEmitExperimentalClientTelemetry = setEmitExperimentalClientTelemetry;
  }

  public static void internalSetEmitExperimentalServerTelemetry(
      BiConsumer<NettyServerTelemetryBuilder, Boolean> setEmitExperimentalServerTelemetry) {
    Experimental.setEmitExperimentalServerTelemetry = setEmitExperimentalServerTelemetry;
  }

  private Experimental() {}
}
