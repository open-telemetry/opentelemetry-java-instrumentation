/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

import static io.opentelemetry.javaagent.instrumentation.powerjob.v4_0.PowerJobConstants.BASIC_PROCESSOR;

import java.util.Arrays;
import java.util.List;
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
  private static final List<Class<?>> KNOWN_PROCESSORS =
      Arrays.asList(
          FileCleanupProcessor.class,
          BroadcastProcessor.class,
          MapReduceProcessor.class,
          MapProcessor.class,
          ShellProcessor.class,
          PythonProcessor.class,
          HttpProcessor.class,
          SpringDatasourceSqlProcessor.class,
          DynamicDatasourceSqlProcessor.class);

  private PowerJobProcessRequest(Long jobId) {
    this.jobId = jobId;
  }

  public static PowerJobProcessRequest createRequest(
      Long jobId,
      BasicProcessor handler,
      String methodName,
      String jobParams,
      String instanceParams) {
    PowerJobProcessRequest request = new PowerJobProcessRequest(jobId);
    request.methodName = methodName;
    request.declaringClass = handler.getClass();
    request.jobParams = jobParams;
    request.instanceParams = instanceParams;
    request.jobType = BASIC_PROCESSOR;

    for (Class<?> processorClass : KNOWN_PROCESSORS) {
      if (processorClass.isInstance(handler)) {
        request.jobType = processorClass.getSimpleName();
        break;
      }
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
