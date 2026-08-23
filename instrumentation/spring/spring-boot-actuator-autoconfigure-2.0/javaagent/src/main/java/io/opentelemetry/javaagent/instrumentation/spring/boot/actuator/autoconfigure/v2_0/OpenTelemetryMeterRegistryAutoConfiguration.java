/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.boot.actuator.autoconfigure.v2_0;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.MicrometerSingletons;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// CompositeMeterRegistryAutoConfiguration configures the "final" composite registry
@AutoConfigureBefore(
    name = {
      "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration", // Spring Boot 2.x-3.x location
      "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration" // Spring Boot 4.x location
    })
// configure after the SimpleMeterRegistry has initialized; it is normally the last MeterRegistry
// implementation to be configured, as it's used as a fallback
// the OTel registry should be added in addition to that fallback and not replace it
@AutoConfigureAfter(
    name = {
      "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration", // Spring Boot 2.x-3.x location
      "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration" // Spring Boot 4.x location
    })
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(MeterRegistry.class)
public class OpenTelemetryMeterRegistryAutoConfiguration {

  @Bean
  public MeterRegistry otelMeterRegistry() {
    return MicrometerSingletons.meterRegistry();
  }
}
