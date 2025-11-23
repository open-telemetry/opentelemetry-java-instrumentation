/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.actuator.v2_0;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.MicrometerSingletons;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// CompositeMeterRegistryAutoConfiguration configures the "final" composite registry
// Spring Boot 2.x-3.x location and Spring Boot 4.x location (package changed in 4.x)
@AutoConfigureBefore(
    name = {
      "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration"
    })
// configure after the SimpleMeterRegistry has initialized; it is normally the last MeterRegistry
// implementation to be configured, as it's used as a fallback
// the OTel registry should be added in addition to that fallback and not replace it
// Spring Boot 2.x-3.x location and Spring Boot 4.x location (package changed in 4.x)
@AutoConfigureAfter(
    name = {
      "org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration",
      "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration"
    })
@ConditionalOnBean(Clock.class)
@ConditionalOnClass(MeterRegistry.class)
public class OpenTelemetryMeterRegistryAutoConfiguration {

  @Bean
  public MeterRegistry otelMeterRegistry() {
    return MicrometerSingletons.meterRegistry();
  }

  @Bean
  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  // must be public because this class is injected as a proxy when using non-inlined advice and that
  // proxy contains only public methods
  public static BeanPostProcessor postProcessCompositeMeterRegistry() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof CompositeMeterRegistry) {
          CompositeMeterRegistry original = (CompositeMeterRegistry) bean;
          List<MeterRegistry> list = new ArrayList<>(original.getRegistries());
          // sort otel registry last since it doesn't support reading metric values
          // and the actuator endpoint reads metrics from the first registry
          list.sort(
              Comparator.comparingInt(
                  value -> value == MicrometerSingletons.meterRegistry() ? 1 : 0));
          Set<MeterRegistry> registries = new LinkedHashSet<>(list);
          return new CompositeMeterRegistry(
              original.config().clock(), Collections.singletonList(original)) {
            @Override
            public Set<MeterRegistry> getRegistries() {
              return registries;
            }
          };
        }
        return bean;
      }
    };
  }
}
