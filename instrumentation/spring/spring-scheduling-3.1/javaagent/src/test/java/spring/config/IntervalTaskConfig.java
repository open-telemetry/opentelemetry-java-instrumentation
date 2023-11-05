/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import spring.component.IntervalTask;

@Configuration
@EnableScheduling
public class IntervalTaskConfig {
  @Bean
  public IntervalTask scheduledTasks() {
    return new IntervalTask();
  }
}
