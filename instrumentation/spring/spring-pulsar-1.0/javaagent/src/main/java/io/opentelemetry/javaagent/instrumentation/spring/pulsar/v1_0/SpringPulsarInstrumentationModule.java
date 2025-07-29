/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.pulsar.v1_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class SpringPulsarInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {
  public SpringPulsarInstrumentationModule() {
    super("spring-pulsar", "spring-pulsar-1.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 1.0.0
    return hasClassesNamed(
        "org.springframework.pulsar.annotation.PulsarListenerConsumerBuilderCustomizer");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new DefaultPulsarMessageListenerContainerInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
