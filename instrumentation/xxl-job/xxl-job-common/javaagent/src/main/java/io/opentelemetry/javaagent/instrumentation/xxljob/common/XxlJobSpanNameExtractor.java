/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import static io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobConstants.SCRIPT_JOB_TYPE;

import com.xxl.job.core.glue.GlueTypeEnum;
import io.opentelemetry.api.internal.StringUtils;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class XxlJobSpanNameExtractor implements SpanNameExtractor<XxlJobProcessRequest> {

  @Override
  public String extract(XxlJobProcessRequest request) {
    GlueTypeEnum glueTypeEnum = request.getGlueTypeEnum();
    if (SCRIPT_JOB_TYPE.contains(glueTypeEnum.getDesc())) {
      return glueTypeEnum.getDesc() + ".ID-" + request.getJobId();
    }
    String methodName = request.getMethodName();
    if (StringUtils.isNullOrEmpty(methodName)) {
      methodName = "execute";
    }
    Class<?> declaringClass = request.getDeclaringClass();
    if (declaringClass != null) {
      return declaringClass.getSimpleName() + "." + methodName;
    } else {
      return "unknown" + "." + methodName;
    }
  }
}
