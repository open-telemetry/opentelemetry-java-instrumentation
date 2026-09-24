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

/**
 * Muzzle evaluates all library references in an {@link InstrumentationModule} together. This module
 * isolates the optional Kafka references so the core Camel instrumentation can load without the
 * Kafka client API.
 */
@AutoService(InstrumentationModule.class)
public class ApacheCamelKafkaInstrumentationModule extends InstrumentationModule {

  public ApacheCamelKafkaInstrumentationModule() {
    super("camel", "camel-2.20", "camel-kafka");
  }

  @Override
  public int order() {
    // Install before Kafka so Camel's exit advice runs after Kafka's exit advice.
    return -1;
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.apache.camel.component.kafka.KafkaConsumer")
        .and(hasClassesNamed("org.apache.kafka.clients.consumer.KafkaConsumer"));
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new CamelMuzzleInstrumentation(),
        new KafkaConsumerInstrumentation(),
        new KafkaFetchRecordsInstrumentation(),
        new KafkaEndpointInstrumentation());
  }
}
