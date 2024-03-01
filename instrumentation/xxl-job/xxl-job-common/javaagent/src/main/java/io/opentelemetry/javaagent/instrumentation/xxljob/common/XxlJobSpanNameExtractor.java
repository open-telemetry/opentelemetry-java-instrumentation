/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import static io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobConstants.SCRIPT_JOB_TYPE;

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
    if (SCRIPT_JOB_TYPE.contains(glueType.getDesc())) {
      return glueType.getDesc() + ".ID-" + request.getJobId();
    }
    return codeSpanNameExtractor.extract(request);
  }
}
