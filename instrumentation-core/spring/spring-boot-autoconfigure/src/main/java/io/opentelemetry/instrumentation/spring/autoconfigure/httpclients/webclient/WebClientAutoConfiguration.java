package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.HttpClientProperties;
import io.opentelemetry.trace.Tracer;


/** Configures RestTemplateBeanPostProcessor bean */
@Configuration
@ConditionalOnClass(WebClient.class)
@EnableConfigurationProperties(HttpClientProperties.class)
@ConditionalOnProperty(prefix = "opentelemetry.trace.httpclients", name = "enabled")
public class WebClientAutoConfiguration {

  @Autowired private Tracer tracer;

  @Bean
  public WebClientBeanPostProcessor webClientBeanPostProcessor() {
    return new WebClientBeanPostProcessor(tracer);
  }
}
