/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.v1_9_2;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobCodeAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobExperimentalAttributeExtractor;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobProcessRequest;
import io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobSpanNameExtractor;

public final class XxlJobSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.xxl-job-1.9.2";

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.xxl-job.experimental-span-attributes", false);

  private static final Instrumenter<XxlJobProcessRequest, Void> XXL_JOB_PROCESS_INSTRUMENTER;

  static {
    XxlJobSpanNameExtractor spanNameExtractor = new XxlJobSpanNameExtractor();
    InstrumenterBuilder<XxlJobProcessRequest, Void> builder =
        Instrumenter.<XxlJobProcessRequest, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(
                CodeAttributesExtractor.create(new XxlJobCodeAttributesGetter()));
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      builder.addAttributesExtractor(
          AttributesExtractor.constant(AttributeKey.stringKey("job.system"), "xxl-job"));
      builder.addAttributesExtractor(new XxlJobExperimentalAttributeExtractor());
    }
    XXL_JOB_PROCESS_INSTRUMENTER = builder.buildInstrumenter();
  }

  public static Instrumenter<XxlJobProcessRequest, Void> xxlJobProcessInstrumenter() {
    return XXL_JOB_PROCESS_INSTRUMENTER;
  }

  private XxlJobSingletons() {}
}
