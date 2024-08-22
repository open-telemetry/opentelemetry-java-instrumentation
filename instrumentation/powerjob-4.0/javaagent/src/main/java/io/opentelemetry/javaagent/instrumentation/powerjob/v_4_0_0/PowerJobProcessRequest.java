/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0;

import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.BASIC_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.BROADCAST_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.DYNAMIC_DATASOURCE_SQL_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.FILE_CLEANUP_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.HTTP_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.MAP_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.MAP_REDUCE_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.PYTHON_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.SHELL_PROCESSOR;
import static io.opentelemetry.javaagent.instrumentation.powerjob.v_4_0_0.PowerJobConstants.SPRING_DATASOURCE_SQL_PROCESSOR;

import tech.powerjob.official.processors.impl.FileCleanupProcessor;
import tech.powerjob.official.processors.impl.HttpProcessor;
import tech.powerjob.official.processors.impl.script.PythonProcessor;
import tech.powerjob.official.processors.impl.script.ShellProcessor;
import tech.powerjob.official.processors.impl.sql.DynamicDatasourceSqlProcessor;
import tech.powerjob.official.processors.impl.sql.SpringDatasourceSqlProcessor;
import tech.powerjob.worker.core.processor.sdk.BasicProcessor;
import tech.powerjob.worker.core.processor.sdk.BroadcastProcessor;
import tech.powerjob.worker.core.processor.sdk.MapProcessor;
import tech.powerjob.worker.core.processor.sdk.MapReduceProcessor;

public final class PowerJobProcessRequest {
  private String methodName;
  private final Long jobId;
  private String jobType;
  private Class<?> declaringClass;

  private boolean failed;

  private String jobParams;
  private String instanceParams;

  private PowerJobProcessRequest(Long jobId) {
    this.jobId = jobId;
  }

  public static PowerJobProcessRequest createRequest(
      Long jobId, BasicProcessor handler, String methodName) {
    PowerJobProcessRequest request = new PowerJobProcessRequest(jobId);
    request.methodName = methodName;
    request.declaringClass = handler.getClass();
    request.jobType = BASIC_PROCESSOR;
    if (handler instanceof BroadcastProcessor) {
      request.jobType = BROADCAST_PROCESSOR;
    }
    if (handler instanceof MapProcessor) {
      request.jobType = MAP_PROCESSOR;
    }
    if (handler instanceof MapReduceProcessor) {
      request.jobType = MAP_REDUCE_PROCESSOR;
    }
    if (handler instanceof ShellProcessor) {
      request.jobType = SHELL_PROCESSOR;
    }
    if (handler instanceof PythonProcessor) {
      request.jobType = PYTHON_PROCESSOR;
    }
    if (handler instanceof HttpProcessor) {
      request.jobType = HTTP_PROCESSOR;
    }
    if (handler instanceof FileCleanupProcessor) {
      request.jobType = FILE_CLEANUP_PROCESSOR;
    }
    if (handler instanceof SpringDatasourceSqlProcessor) {
      request.jobType = SPRING_DATASOURCE_SQL_PROCESSOR;
    }
    if (handler instanceof DynamicDatasourceSqlProcessor) {
      request.jobType = DYNAMIC_DATASOURCE_SQL_PROCESSOR;
    }
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

  public String getJobType() {
    return jobType;
  }
}
