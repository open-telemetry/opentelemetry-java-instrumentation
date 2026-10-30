/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static org.springframework.beans.factory.support.BeanDefinitionBuilder.genericBeanDefinition;

import io.opentelemetry.instrumentation.spring.integration.v4_1.SpringIntegrationTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.integration.channel.interceptor.GlobalChannelInterceptorWrapper;

class SpringIntegration41Test extends AbstractSpringIntegration41Test {
  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  SpringIntegration41Test() {
    super(testing);
  }

  @Override
  void configure(AnnotationConfigApplicationContext context) {
    context.registerBeanDefinition(
        "otelGlobalChannelInterceptor",
        genericBeanDefinition(GlobalChannelInterceptorWrapper.class)
            .addConstructorArgValue(
                SpringIntegrationTelemetry.create(testing.getOpenTelemetry())
                    .createChannelInterceptor())
            .addPropertyValue("patterns", new String[] {"*"})
            .getBeanDefinition());
  }
}
