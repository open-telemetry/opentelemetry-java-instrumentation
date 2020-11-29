/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class LambdaTaskConfig {

  @Bean
  LambdaTaskConfigurer lambdaTaskConfigurer() {
    return new LambdaTaskConfigurer();
  }
}
