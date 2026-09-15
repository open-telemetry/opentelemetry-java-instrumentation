/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.context.Context;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import org.springframework.kafka.listener.RecordInterceptor;

public final class SpringKafkaTelemetryAccess {

  public static <K, V> RecordInterceptor<K, V> createRecordInterceptor(
      SpringKafkaTelemetry telemetry,
      @Nullable RecordInterceptor<K, V> decoratedInterceptor,
      UnaryOperator<Context> contextCustomizer) {
    return telemetry.createRecordInterceptor(decoratedInterceptor, contextCustomizer);
  }

  private SpringKafkaTelemetryAccess() {}
}
