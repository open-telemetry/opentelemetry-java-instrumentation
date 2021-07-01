package io.opentelemetry.instrumentation.spring.autoconfigure.propagators;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
//import io.opentelemetry.instrumentation.spring.autoconfigure.propagators.CompositePropagator;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
//import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;

@Configuration
@AutoConfigureBefore(OpenTelemetryAutoConfiguration.class)
@ConditionalOnProperty(prefix = "otel.propagation", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PropagationProperties.class)
public class PropagationAutoConfiguration {

  @Bean
  ContextPropagators contextPropagators(ObjectProvider<List<TextMapPropagator>> propagators) {
    List<TextMapPropagator> mapPropagators = propagators.getIfAvailable(ArrayList::new);
    if (mapPropagators.isEmpty()) {
      return ContextPropagators.noop();
    }
    return ContextPropagators.create(TextMapPropagator.composite(mapPropagators));
  }

  @Configuration
  static class PropagatorsConfiguration {

    @Bean
    TextMapPropagator compositeTextMapPropagator(BeanFactory beanFactory, PropagationProperties properties) {
      return new CompositeTextMapPropagator(beanFactory, properties.getType());
    }

  }


}
