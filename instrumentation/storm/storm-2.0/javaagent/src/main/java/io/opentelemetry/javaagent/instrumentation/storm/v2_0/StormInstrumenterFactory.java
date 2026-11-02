/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import org.apache.storm.tuple.TupleImpl;

final class StormInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.storm-2.0";

  // messaging.operation.name values, named after the Storm API operations
  private static final String PUBLISH_OPERATION_NAME = "publish";
  private static final String PROCESS_OPERATION_NAME = "process";

  static Instrumenter<StormEmit, Void> createProducerInstrumenter(OpenTelemetry openTelemetry) {
    StormProducerAttributesGetter getter = new StormProducerAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    InstrumenterBuilder<StormEmit, Void> builder =
        Instrumenter.<StormEmit, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PUBLISH_OPERATION_NAME))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operationType, PUBLISH_OPERATION_NAME)
                    .build())
            .addOperationMetrics(MessagingProducerMetrics.getForOperationType());
    setMessagingSendExceptionEventExtractor(builder);
    return builder.buildProducerInstrumenter(StormHeadersSetter.INSTANCE);
  }

  static Instrumenter<TupleImpl, Void> createProcessInstrumenter(OpenTelemetry openTelemetry) {
    StormProcessAttributesGetter getter = new StormProcessAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<TupleImpl, Void> builder =
        Instrumenter.<TupleImpl, Void>builder(
                openTelemetry,
                INSTRUMENTATION_NAME,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                MessagingAttributesExtractor.builder(getter, operationType, PROCESS_OPERATION_NAME)
                    .build())
            .addOperationMetrics(MessagingProcessMetrics.get());
    if (emitStableMessagingSemconv()) {
      // no receive spans are created, so consumed messages are counted on the process span
      builder.addOperationMetrics(MessagingConsumerMetrics.getConsumedMessages());
    }
    setMessagingProcessExceptionEventExtractor(builder);
    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        StormHeadersGetter.INSTANCE,
        false);
  }

  private StormInstrumenterFactory() {}
}
