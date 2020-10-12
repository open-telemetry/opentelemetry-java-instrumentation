/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean

@SpringBootConfiguration
@EnableAutoConfiguration
class TwoServicesConfig {

  @Bean
  ServiceOneRoute serviceOneRoute() {
    return new ServiceOneRoute()
  }

  @Bean
  ServiceTwoRoute serviceTwoRoute() {
    return new ServiceTwoRoute()
  }
}
