/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import spring.component.OneTimeTask;

@Configuration
@EnableScheduling
public class OneTimeTaskConfig {
  @Bean
  public OneTimeTask oneTimeTask() {
    return new OneTimeTask();
  }
}
