/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.xxljob.common;

import com.xxl.job.core.glue.GlueTypeEnum;

public class XxlJobProcessRequest {

  private String methodName;

  private int jobId;

  private Class<?> declaringClass;

  private String resultStatus = "true";

  private GlueTypeEnum glueTypeEnum;

  public String getResultStatus() {
    return resultStatus;
  }

  public void setResultStatus(String resultStatus) {
    this.resultStatus = resultStatus;
  }

  public String getMethodName() {
    return methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public int getJobId() {
    return jobId;
  }

  public void setJobId(int jobId) {
    this.jobId = jobId;
  }

  public Class<?> getDeclaringClass() {
    return declaringClass;
  }

  public void setDeclaringClass(Class<?> declaringClass) {
    this.declaringClass = declaringClass;
  }

  public GlueTypeEnum getGlueTypeEnum() {
    return glueTypeEnum;
  }

  public void setGlueTypeEnum(GlueTypeEnum glueTypeEnum) {
    this.glueTypeEnum = glueTypeEnum;
  }
}
