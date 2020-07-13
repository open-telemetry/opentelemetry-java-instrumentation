package io.opentelemetry.instrumentation.spring.autoconfigure.httpclients.webclient;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import io.opentelemetry.instrumentation.springwebflux.client.WebClientTracingFilter;
import io.opentelemetry.trace.Tracer;

/**
 * Inspired by: <br>
 * https://github.com/spring-cloud/spring-cloud-sleuth/blob/master/spring-cloud-sleuth-core/src/main/java/org/springframework/cloud/sleuth/instrument/web/client/TraceWebClientAutoConfiguration.java
 *
 */
final class WebClientBeanPostProcessor implements BeanPostProcessor {

  private final Tracer tracer;

  public WebClientBeanPostProcessor(Tracer tracer) {
    this.tracer = tracer;
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    return bean;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (bean instanceof WebClient) {
      WebClient webClient = (WebClient) bean;
      return wrapBuilder(webClient.mutate()).build();
    } else if (bean instanceof WebClient.Builder) {
      WebClient.Builder webClientBuilder = (WebClient.Builder) bean;
      return wrapBuilder(webClientBuilder);
    }
    return bean;
  }

  private WebClient.Builder wrapBuilder(WebClient.Builder webClientBuilder) {
    return webClientBuilder.filters(addWebClientFilterFunctionIfNotPresent());
  }

  private Consumer<List<ExchangeFilterFunction>> addWebClientFilterFunctionIfNotPresent() {
    return functions -> {
      boolean noneMatch = noneMatchWebClientTracingFilter(functions);
      if (noneMatch) {
        WebClientTracingFilter.addFilter(functions, tracer);
      }
    };
  }

  private boolean noneMatchWebClientTracingFilter(List<ExchangeFilterFunction> functions) {
    for (ExchangeFilterFunction function : functions) {
      if (function instanceof WebClientTracingFilter) {
        return false;
      }
    }
    return true;
  }
}
