/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import javax.batch.operations.JobOperator;
import javax.batch.runtime.BatchRuntime;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.batch.core.JobParameter;

public class JavaxBatchConfigRunner implements BeforeEachCallback, JobRunner {
  static JobOperator jobOperator;
  static AtomicInteger counter = new AtomicInteger();

  @Override
  public void beforeEach(ExtensionContext context) {
    jobOperator = BatchRuntime.getJobOperator();
  }

  @Override
  public void runJob(String jobName, Map<String, JobParameter> params) {
    Properties jobParams = new Properties();
    params.forEach((k, v) -> jobParams.setProperty(k, v.getValue().toString()));
    // each job instance with the same name needs to be unique
    jobParams.setProperty("uniqueJobIdCounter", String.valueOf(counter.getAndIncrement()));
    jobOperator.start(jobName, jobParams);
  }
}
