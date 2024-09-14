/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.powerjob.v4_0;

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
  private final String methodName;
  private final Long jobId;
  private final String jobType;
  private final Class<?> declaringClass;
  private final String jobParams;
  private final String instanceParams;
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

  private PowerJobProcessRequest(
      Long jobId,
      String methodName,
      Class<?> declaringClass,
      String jobParams,
      String instanceParams,
      String jobType) {
    this.jobId = jobId;
    this.methodName = methodName;
    this.jobType = jobType;
    this.declaringClass = declaringClass;
    this.jobParams = jobParams;
    this.instanceParams = instanceParams;
  }

  public static PowerJobProcessRequest createRequest(
      Long jobId,
      BasicProcessor handler,
      String methodName,
      String jobParams,
      String instanceParams) {
    String jobType = "BasicProcessor";
    for (Class<?> processorClass : KNOWN_PROCESSORS) {
      if (processorClass.isInstance(handler)) {
        jobType = processorClass.getSimpleName();
        break;
      }
    }
    return new PowerJobProcessRequest(
        jobId, methodName, handler.getClass(), jobParams, instanceParams, jobType);
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

  public String getInstanceParams() {
    return instanceParams;
  }

  public String getJobType() {
    return jobType;
  }
}
