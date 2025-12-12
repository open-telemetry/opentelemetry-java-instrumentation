/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;

public final class ElasticJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-elasticjob-3.0";
  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "apache_elasticjob",
              "experimental_span_attributes")
          .orElse(false);

  private static final Instrumenter<ElasticJobProcessRequest, Void> INSTRUMENTER =
      createInstrumenter();
  private static final ElasticJobHelper HELPER = ElasticJobHelper.create(INSTRUMENTER);

  private static Instrumenter<ElasticJobProcessRequest, Void> createInstrumenter() {
    ElasticJobCodeAttributesGetter codeAttributesGetter = new ElasticJobCodeAttributesGetter();
    InstrumenterBuilder<ElasticJobProcessRequest, Void> builder =
        Instrumenter.<ElasticJobProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter));
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "elasticjob"));
      builder.addAttributesExtractor(new ElasticJobExperimentalAttributeExtractor());
    }

    return builder.buildInstrumenter();
  }

  public static ElasticJobHelper helper() {
    return HELPER;
  }

  private ElasticJobSingletons() {}
}
