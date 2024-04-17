/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.reactornetty;

import org.springframework.beans.factory.config.BeanPostProcessor;
import reactor.netty.http.server.HttpServer;

final class HttpServerBeanPostProcessor implements BeanPostProcessor {

  HttpServerBeanPostProcessor() {
  }

  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof HttpServer) {
      HttpServer httpServer = (HttpServer) bean;
      return httpServer.metrics(true, s -> s);
    }
    return bean;
  }
}
