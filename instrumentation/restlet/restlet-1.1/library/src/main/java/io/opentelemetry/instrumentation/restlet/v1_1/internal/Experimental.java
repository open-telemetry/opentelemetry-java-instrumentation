/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1.internal;

import io.opentelemetry.instrumentation.restlet.v1_1.RestletTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<RestletTelemetryBuilder, Boolean> setEmitExperimentalTelemetry;

  public static void setEmitExperimentalTelemetry(
      RestletTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalTelemetry != null) {
      setEmitExperimentalTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void internalSetEmitExperimentalTelemetry(
      BiConsumer<RestletTelemetryBuilder, Boolean> setEmitExperimentalTelemetry) {
    Experimental.setEmitExperimentalTelemetry = setEmitExperimentalTelemetry;
  }

  private Experimental() {}
}
