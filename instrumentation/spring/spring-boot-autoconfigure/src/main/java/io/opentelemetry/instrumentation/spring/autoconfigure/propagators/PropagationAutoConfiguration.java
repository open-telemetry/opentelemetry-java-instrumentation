/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures {@link ContextPropagators} bean for propagation. */
@Configuration
@EnableConfigurationProperties(PropagationProperties.class)
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "otel.propagation", name = "enabled", matchIfMissing = true)
public class PropagationAutoConfiguration {

  private static final List<String> DEFAULT_PROPAGATORS = Arrays.asList("tracecontext", "baggage");

  @Bean
  @ConditionalOnMissingBean
  ContextPropagators contextPropagators(ObjectProvider<List<TextMapPropagator>> propagators) {
    List<TextMapPropagator> mapPropagators = propagators.getIfAvailable(Collections::emptyList);
    if (mapPropagators.isEmpty()) {
      return ContextPropagators.noop();
    }
    return ContextPropagators.create(TextMapPropagator.composite(mapPropagators));
  }

  @Configuration
  static class PropagatorsConfiguration {

    @Bean
    TextMapPropagator compositeTextMapPropagator(
        BeanFactory beanFactory, ConfigProperties configProperties) {
      return CompositeTextMapPropagatorFactory.getCompositeTextMapPropagator(
          beanFactory, configProperties.getList("otel.propagators", DEFAULT_PROPAGATORS));
    }
  }
}
