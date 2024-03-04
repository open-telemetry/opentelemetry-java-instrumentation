package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.reactornetty.v1_0.ReactorNettyTelemetryBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.netty.http.client.HttpClient;

public class HttpClientBeanPostProcessor implements BeanPostProcessor {
  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

  HttpClientBeanPostProcessor(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
    if (openTelemetry == null) {
      return bean;
    }
    if (bean instanceof HttpClient) {
      HttpClient httpClient = (HttpClient) bean;
      ReactorNettyTelemetryBuilder builder = new ReactorNettyTelemetryBuilder(openTelemetry);
      bean = builder.build().tracingHttpClient(httpClient);
    }
    return bean;
  }
}
