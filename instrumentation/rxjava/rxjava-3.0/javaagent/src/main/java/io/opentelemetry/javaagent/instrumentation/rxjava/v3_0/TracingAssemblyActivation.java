/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rxjava.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.rxjava.v3_0.TracingAssembly;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TracingAssemblyActivation {

  private static final ClassValue<AtomicBoolean> activated =
      new ClassValue<AtomicBoolean>() {
        @Override
        protected AtomicBoolean computeValue(Class<?> type) {
          return new AtomicBoolean();
        }
      };

  public static void activate(Class<?> clz) {
    if (activated.get(clz).compareAndSet(false, true)) {
      TracingAssembly.builder()
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getBoolean(
                      GlobalOpenTelemetry.get(), "java", "rxjava", "span_attributes/development")
                  .orElse(false))
          .build()
          .enable();
    }
  }

  private TracingAssemblyActivation() {}
}
