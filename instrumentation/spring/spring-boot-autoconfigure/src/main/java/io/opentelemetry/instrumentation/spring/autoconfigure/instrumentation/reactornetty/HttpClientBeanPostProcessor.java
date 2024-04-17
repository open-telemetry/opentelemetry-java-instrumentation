/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.netty.http.client.HttpClient;

final class HttpClientBeanPostProcessor implements BeanPostProcessor {

  HttpClientBeanPostProcessor() {
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof HttpClient) {
      HttpClient httpClient = (HttpClient) bean;
      return httpClient.metrics(true, s -> s);
    }
    return bean;
  }
}
