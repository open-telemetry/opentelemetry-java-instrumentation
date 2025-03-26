/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.ConfigurableApplicationContext;

public class ApplicationConfigRunner implements BeforeEachCallback, AfterEachCallback, JobRunner {
  private final Supplier<ConfigurableApplicationContext> applicationContextFactory;
  private final BiConsumer<String, Job> jobPostProcessor;
  static JobLauncher jobLauncher;
  private ConfigurableApplicationContext applicationContext;

  public ApplicationConfigRunner(
      Supplier<ConfigurableApplicationContext> applicationContextFactory) {
    this(applicationContextFactory, (jobName, job) -> {});
  }

  public ApplicationConfigRunner(
      Supplier<ConfigurableApplicationContext> applicationContextFactory,
      BiConsumer<String, Job> jobPostProcessor) {
    this.applicationContextFactory = applicationContextFactory;
    this.jobPostProcessor = jobPostProcessor;
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    applicationContext = applicationContextFactory.get();
    applicationContext.start();

    jobLauncher = applicationContext.getBean(JobLauncher.class);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    applicationContext.stop();
    applicationContext.close();
  }

  @Override
  public void runJob(String jobName, Map<String, JobParameter> params) {
    Job job = applicationContext.getBean(jobName, Job.class);
    jobPostProcessor.accept(jobName, job);
    try {
      jobLauncher.run(job, new JobParameters(params));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
