/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

public final class XxlJobProcessRequest {

  @Nullable private final String methodName;
  private final int jobId;
  @Nullable private final Class<?> declaringClass;
  private boolean failed;
  private final GlueTypeEnum glueType;

  public static XxlJobProcessRequest createRequestForMethod(
      GlueTypeEnum glueType, Class<?> declaringClass, @Nullable String methodName) {
    return new XxlJobProcessRequest(glueType, declaringClass, methodName, 0);
  }

  public static XxlJobProcessRequest createGlueJobRequest(IJobHandler handler) {
    return createRequestForMethod(GlueTypeEnum.GLUE_GROOVY, handler.getClass(), "execute");
  }

  public static XxlJobProcessRequest createScriptJobRequest(GlueTypeEnum glueType, int jobId) {
    return new XxlJobProcessRequest(glueType, null, null, jobId);
  }

  public static XxlJobProcessRequest createSimpleJobRequest(IJobHandler handler) {
    return createRequestForMethod(GlueTypeEnum.BEAN, handler.getClass(), "execute");
  }

  public static XxlJobProcessRequest createMethodJobRequest(
      Object target, @Nullable Method method) {
    return createRequestForMethod(
        GlueTypeEnum.BEAN, target.getClass(), method != null ? method.getName() : null);
  }

  private XxlJobProcessRequest(
      GlueTypeEnum glueType,
      @Nullable Class<?> declaringClass,
      @Nullable String methodName,
      int jobId) {
    this.glueType = glueType;
    this.declaringClass = declaringClass;
    this.methodName = methodName;
    this.jobId = jobId;
  }

  public void setFailed() {
    failed = true;
  }

  public boolean isFailed() {
    return failed;
  }

  @Nullable
  public String getMethodName() {
    return methodName;
  }

  public int getJobId() {
    return jobId;
  }

  @Nullable
  public Class<?> getDeclaringClass() {
    return declaringClass;
  }

  public GlueTypeEnum getGlueType() {
    return glueType;
  }
}
