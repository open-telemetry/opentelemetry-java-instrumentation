/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import com.xxl.job.core.glue.GlueTypeEnum;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

class XxlJobSpanNameExtractor implements SpanNameExtractor<XxlJobProcessRequest> {
  private final SpanNameExtractor<XxlJobProcessRequest> codeSpanNameExtractor;

  XxlJobSpanNameExtractor(CodeAttributesGetter<XxlJobProcessRequest> getter) {
    codeSpanNameExtractor = CodeSpanNameExtractor.create(getter);
  }

  @Override
  public String extract(XxlJobProcessRequest request) {
    GlueTypeEnum glueType = request.getGlueType();
    if (glueType.isScript()) {
      // TODO: need to discuss a better span name for script job in the future.
      // for detail can refer to
      // https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/10421#discussion_r1511532584
      return glueType.getDesc();
    }
    return codeSpanNameExtractor.extract(request);
  }
}
