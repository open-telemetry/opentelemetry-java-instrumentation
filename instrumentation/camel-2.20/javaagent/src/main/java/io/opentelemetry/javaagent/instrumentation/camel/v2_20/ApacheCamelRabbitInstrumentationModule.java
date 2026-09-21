/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class ApacheCamelRabbitInstrumentationModule extends InstrumentationModule {

  public ApacheCamelRabbitInstrumentationModule() {
    super("camel", "camel-2.20", "camel-rabbitmq");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.camel.component.rabbitmq.RabbitConsumer")
        .and(hasClassesNamed("com.rabbitmq.client.Consumer"));
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CamelMuzzleInstrumentation(), new RabbitConsumerInstrumentation());
  }
}
