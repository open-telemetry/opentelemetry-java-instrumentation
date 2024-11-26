/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import com.xxl.job.core.glue.GlueTypeEnum;
import com.xxl.job.core.handler.IJobHandler;
import java.lang.reflect.Method;

public final class XxlJobProcessRequest {

  private String methodName;
  private int jobId;
  private Class<?> declaringClass;
  private boolean failed;
  private final GlueTypeEnum glueType;

  private XxlJobProcessRequest(GlueTypeEnum glueType) {
    this.glueType = glueType;
  }

  public static XxlJobProcessRequest createRequestForMethod(
      GlueTypeEnum glueType, Class<?> declaringClass, String methodName) {
    XxlJobProcessRequest request = new XxlJobProcessRequest(glueType);
    request.declaringClass = declaringClass;
    request.methodName = methodName;

    return request;
  }

  public static XxlJobProcessRequest createGlueJobRequest(IJobHandler handler) {
    return createRequestForMethod(GlueTypeEnum.GLUE_GROOVY, handler.getClass(), "execute");
  }

  public static XxlJobProcessRequest createScriptJobRequest(GlueTypeEnum glueType, int jobId) {
    XxlJobProcessRequest request = new XxlJobProcessRequest(glueType);
    request.jobId = jobId;

    return request;
  }

  public static XxlJobProcessRequest createSimpleJobRequest(IJobHandler handler) {
    return createRequestForMethod(GlueTypeEnum.BEAN, handler.getClass(), "execute");
  }

  public static XxlJobProcessRequest createMethodJobRequest(Object target, Method method) {
    return createRequestForMethod(
        GlueTypeEnum.BEAN, target.getClass(), method != null ? method.getName() : null);
  }

  public void setFailed() {
    failed = true;
  }

  public boolean isFailed() {
    return failed;
  }

  public String getMethodName() {
    return methodName;
  }

  public int getJobId() {
    return jobId;
  }

  public Class<?> getDeclaringClass() {
    return declaringClass;
  }

  public GlueTypeEnum getGlueType() {
    return glueType;
  }
}
