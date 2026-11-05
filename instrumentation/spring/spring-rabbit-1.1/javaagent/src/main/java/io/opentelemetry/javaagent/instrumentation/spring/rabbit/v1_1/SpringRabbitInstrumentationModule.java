/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringRabbitInstrumentationModule extends InstrumentationModule {
  public SpringRabbitInstrumentationModule() {
    super("spring-rabbit-1.1", "spring-rabbit");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.1.0.RELEASE
    return hasClassesNamed("org.springframework.amqp.rabbit.config.RabbitNamespaceUtils");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractMessageListenerContainerInstrumentation(),
        new SpringRabbitConsumerInstrumentation(),
        new SimpleMessageListenerContainerInstrumentation());
  }
}
