/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaClientTelemetryBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaServerTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<ArmeriaClientTelemetryBuilder, Boolean>
      setEmitExperimentalClientTelemetry;

  @Nullable
  private static volatile BiConsumer<ArmeriaServerTelemetryBuilder, Boolean>
      setEmitExperimentalServerTelemetry;

  @Nullable
  private static volatile BiConsumer<ArmeriaClientTelemetryBuilder, String> setClientPeerService;

  /**
   * Sets whether experimental HTTP client telemetry should be emitted.
   *
   * @param builder the client telemetry builder
   * @param emitExperimentalTelemetry {@code true} to emit experimental telemetry
   */
  public static void setEmitExperimentalTelemetry(
      ArmeriaClientTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalClientTelemetry != null) {
      setEmitExperimentalClientTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  /**
   * Sets whether experimental HTTP server telemetry should be emitted.
   *
   * @param builder the server telemetry builder
   * @param emitExperimentalTelemetry {@code true} to emit experimental telemetry
   */
  public static void setEmitExperimentalTelemetry(
      ArmeriaServerTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalServerTelemetry != null) {
      setEmitExperimentalServerTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  /**
   * Sets the {@code peer.service} attribute for the client telemetry.
   *
   * @param builder the client telemetry builder
   * @param peerService the peer service name
   */
  public static void setClientPeerService(
      ArmeriaClientTelemetryBuilder builder, String peerService) {
    if (setClientPeerService != null) {
      setClientPeerService.accept(builder, peerService);
    }
  }

  public static void internalSetEmitExperimentalClientTelemetry(
      BiConsumer<ArmeriaClientTelemetryBuilder, Boolean> setEmitExperimentalClientTelemetry) {
    Experimental.setEmitExperimentalClientTelemetry = setEmitExperimentalClientTelemetry;
  }

  public static void internalSetEmitExperimentalServerTelemetry(
      BiConsumer<ArmeriaServerTelemetryBuilder, Boolean> setEmitExperimentalServerTelemetry) {
    Experimental.setEmitExperimentalServerTelemetry = setEmitExperimentalServerTelemetry;
  }

  public static void internalSetClientPeerService(
      BiConsumer<ArmeriaClientTelemetryBuilder, String> setClientPeerService) {
    Experimental.setClientPeerService = setClientPeerService;
  }

  private Experimental() {}
}
