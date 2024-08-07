/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0;

public final class PowerJobProcessRequest {
  private String methodName;
  private final Long jobId;
  private Class<?> declaringClass;

  private boolean failed;

  private String jobParams;
  private String instanceParams;

  private PowerJobProcessRequest(Long jobId) {
    this.jobId = jobId;
  }

  public static PowerJobProcessRequest createRequest(Long jobId, Class<?> declaringClass, String methodName) {
    PowerJobProcessRequest request = new PowerJobProcessRequest(jobId);
    request.methodName = methodName;
    request.declaringClass = declaringClass;
    return request;
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

  public Long getJobId() {
    return jobId;
  }

  public Class<?> getDeclaringClass() {
    return declaringClass;
  }

  public String getJobParams() {
    return jobParams;
  }

  public void setJobParams(String jobParams) {
    this.jobParams = jobParams;
  }

  public String getInstanceParams() {
    return instanceParams;
  }

  public void setInstanceParams(String instanceParams) {
    this.instanceParams = instanceParams;
  }
}
