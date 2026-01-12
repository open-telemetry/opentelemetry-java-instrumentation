/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.jms.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringJmsInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public SpringJmsInstrumentationModule() {
    super("spring-jms", "spring-jms-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // introduced in 2.0, removed in 6.0
    return hasClassesNamed("org.springframework.jms.remoting.JmsInvokerProxyFactoryBean");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Arrays.asList(
        new SpringJmsMessageListenerInstrumentation(),
        new JmsDestinationAccessorInstrumentation(),
        new AbstractPollingMessageListenerContainerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
