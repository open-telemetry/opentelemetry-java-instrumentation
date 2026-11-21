/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PetclinicStartupUptimeAutoConfiguration {
  @Bean
  public PetclinicStartupUptimeResponseAdvice petclinicStartupUptimeResponseAdvice() {
    return new PetclinicStartupUptimeResponseAdvice();
  }
}
