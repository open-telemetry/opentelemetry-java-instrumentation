/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class KafkaConnectBatchAttributesExtractor
    implements AttributesExtractor<KafkaConnectTask, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, KafkaConnectTask request) {
    if (emitStableMessagingSemconv()) {
      request.getBatchRecordAttributes().putCommonAttributes(attributes);
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      KafkaConnectTask request,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
