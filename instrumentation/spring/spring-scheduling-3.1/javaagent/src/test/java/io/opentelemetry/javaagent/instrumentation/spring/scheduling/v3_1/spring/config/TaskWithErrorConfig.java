/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.config;

import io.opentelemetry.instrumentation.testing.GlobalTraceUtil;
import io.opentelemetry.javaagent.instrumentation.spring.scheduling.v3_1.spring.component.TaskWithError;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

@Configuration
@EnableScheduling
public class TaskWithErrorConfig {
  @Bean
  public TaskWithError task() {
    return new TaskWithError();
  }

  @Bean
  public TaskScheduler taskScheduler() {
    ConcurrentTaskScheduler scheduler = new ConcurrentTaskScheduler();
    scheduler.setErrorHandler(throwable -> GlobalTraceUtil.runWithSpan("error-handler", () -> {}));
    return scheduler;
  }
}
