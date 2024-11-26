/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import com.xxl.job.core.glue.GlueTypeEnum;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

class XxlJobCodeAttributesGetter implements CodeAttributesGetter<XxlJobProcessRequest> {

  @Nullable
  @Override
  public Class<?> getCodeClass(XxlJobProcessRequest xxlJobProcessRequest) {
    GlueTypeEnum glueType = xxlJobProcessRequest.getGlueType();
    if (!glueType.isScript()) {
      return xxlJobProcessRequest.getDeclaringClass();
    }
    return null;
  }

  @Nullable
  @Override
  public String getMethodName(XxlJobProcessRequest xxlJobProcessRequest) {
    GlueTypeEnum glueType = xxlJobProcessRequest.getGlueType();
    if (!glueType.isScript()) {
      return xxlJobProcessRequest.getMethodName();
    }
    return null;
  }
}
