/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class ElasticJobInstrumenterFactory {
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.apache-elasticjob.experimental-span-attributes", false);

  public static Instrumenter<ElasticJobProcessRequest, Void> create(String instrumentationName) {
    ElasticJobCodeAttributesGetter codeAttributesGetter = new ElasticJobCodeAttributesGetter();
    InstrumenterBuilder<ElasticJobProcessRequest, Void> builder =
        Instrumenter.<ElasticJobProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                new ElasticJobSpanNameExtractor(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .setSpanStatusExtractor(
                (spanStatusBuilder, elasticJobProcessRequest, unused, error) -> {
                  if (error != null || elasticJobProcessRequest.isFailed()) {
                    spanStatusBuilder.setStatus(StatusCode.ERROR);
                  }
                });
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "elasticjob"));
      builder.addAttributesExtractor(new ElasticJobExperimentalAttributeExtractor());
    }

    return builder.buildInstrumenter();
  }

  private ElasticJobInstrumenterFactory() {}
}
