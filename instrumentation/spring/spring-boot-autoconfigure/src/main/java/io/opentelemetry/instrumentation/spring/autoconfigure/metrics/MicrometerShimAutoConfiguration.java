/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MicrometerShimProperties.class)
@ConditionalOnProperty(name = "otel.springboot.micrometer.enabled", matchIfMissing = true)
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@AutoConfigureBefore(CompositeMeterRegistryAutoConfiguration.class)
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(MeterRegistry.class)
public class MicrometerShimAutoConfiguration {

  @Bean
  public MeterRegistry micrometerShim(OpenTelemetry openTelemetry, Clock micrometerClock) {
    return OpenTelemetryMeterRegistry.builder(openTelemetry).setClock(micrometerClock).build();
  }
}
