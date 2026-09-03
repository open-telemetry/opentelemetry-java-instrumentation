/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

/**
 * Keeps the optional Kafka bridge in its own module because Muzzle validates library references at
 * {@link InstrumentationModule} scope. The core Camel module and JMS bridge remain active when the
 * Kafka client API is absent.
 */
@AutoService(InstrumentationModule.class)
public class ApacheCamelKafkaInstrumentationModule extends InstrumentationModule {

  public ApacheCamelKafkaInstrumentationModule() {
    super("camel", "camel-2.20", "camel-kafka");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.opentelemetry.contrib.awsxray.");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new CamelMuzzleInstrumentation(), new KafkaEndpointInstrumentation());
  }
}
