/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringIntegrationAmqpInstrumentationModule extends InstrumentationModule {

  public SpringIntegrationAmqpInstrumentationModule() {
    super("spring-integration", "spring-integration-4.1");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.springframework.messaging.support.ExecutorChannelInterceptor")
        .and(
            hasClassesNamed(
                "org.springframework.integration.amqp.inbound.AmqpInboundChannelAdapter"))
        .and(hasClassesNamed("org.springframework.amqp.core.Message"))
        .and(hasClassesNamed("com.rabbitmq.client.Channel"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AmqpInboundChannelAdapterInstrumentation());
  }
}
