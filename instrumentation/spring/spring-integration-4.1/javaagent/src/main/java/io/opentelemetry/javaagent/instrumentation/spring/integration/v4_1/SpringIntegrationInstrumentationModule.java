/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringIntegrationInstrumentationModule extends InstrumentationModule {
  public SpringIntegrationInstrumentationModule() {
    super("spring-integration-4.1", "spring-integration");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 4.1.0.RELEASE
    return hasClassesNamed("org.springframework.messaging.support.ChannelInterceptor");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ApplicationContextInstrumentation(),
        new MessageHistoryInstrumentation(),
        new MessageProducerSupportInstrumentation());
  }
}
