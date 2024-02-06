/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import static io.opentelemetry.javaagent.instrumentation.xxljob.common.XxlJobConstants.SCRIPT_JOB_TYPE;

import com.xxl.job.core.glue.GlueTypeEnum;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

public class XxlJobCodeAttributesGetter implements CodeAttributesGetter<XxlJobProcessRequest> {

  @Nullable
  @Override
  public Class<?> getCodeClass(XxlJobProcessRequest xxlJobProcessRequest) {
    GlueTypeEnum glueTypeEnum = xxlJobProcessRequest.getGlueTypeEnum();
    if (!SCRIPT_JOB_TYPE.contains(glueTypeEnum.getDesc())) {
      return xxlJobProcessRequest.getDeclaringClass();
    }
    return null;
  }

  @Nullable
  @Override
  public String getMethodName(XxlJobProcessRequest xxlJobProcessRequest) {
    GlueTypeEnum glueTypeEnum = xxlJobProcessRequest.getGlueTypeEnum();
    if (!SCRIPT_JOB_TYPE.contains(glueTypeEnum.getDesc())) {
      String methodName = xxlJobProcessRequest.getMethodName();
      if (methodName == null || methodName.isEmpty()) {
        return "execute";
      } else {
        return methodName;
      }
    }
    return "ID-" + xxlJobProcessRequest.getJobId();
  }
}
