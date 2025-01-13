/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.instrumentation.httpclient.JavaHttpClientTelemetryBuilder;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<JavaHttpClientTelemetryBuilder, Boolean>
      setEmitExperimentalTelemetry;

  public static void setEmitExperimentalTelemetry(
      JavaHttpClientTelemetryBuilder builder, boolean emitExperimentalTelemetry) {
    if (setEmitExperimentalTelemetry != null) {
      setEmitExperimentalTelemetry.accept(builder, emitExperimentalTelemetry);
    }
  }

  public static void internalSetEmitExperimentalTelemetry(
      BiConsumer<JavaHttpClientTelemetryBuilder, Boolean> setEmitExperimentalTelemetry) {
    Experimental.setEmitExperimentalTelemetry = setEmitExperimentalTelemetry;
  }

  private Experimental() {}
}
