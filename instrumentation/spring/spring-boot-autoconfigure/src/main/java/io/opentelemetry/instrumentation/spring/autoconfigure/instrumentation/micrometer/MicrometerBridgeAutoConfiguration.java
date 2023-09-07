/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.micrometer;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@AutoConfigureAfter(MetricsAutoConfiguration.class)
@AutoConfigureBefore(CompositeMeterRegistryAutoConfiguration.class)
@ConditionalOnBean({Clock.class, OpenTelemetry.class})
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(name = "otel.instrumentation.micrometer.enabled", matchIfMissing = true)
@Configuration
public class MicrometerBridgeAutoConfiguration {

  @Bean
  MeterRegistry otelMeterRegistry(OpenTelemetry openTelemetry, Clock micrometerClock) {
    return OpenTelemetryMeterRegistry.builder(openTelemetry).setClock(micrometerClock).build();
  }
}
