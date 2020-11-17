/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class KafkaClientsInstrumentationModule extends InstrumentationModule {
  public KafkaClientsInstrumentationModule() {
    super("kafka-clients", "kafka-clients-0.11", "kafka");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".KafkaClientConfiguration",
      packageName + ".KafkaConsumerTracer",
      packageName + ".TextMapExtractAdapter",
      packageName + ".TracingIterable",
      packageName + ".TracingIterator",
      packageName + ".TracingList",
      packageName + ".KafkaProducerTracer",
      packageName + ".TextMapInjectAdapter",
      packageName + ".ProducerCallback"
    };
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new KafkaConsumerInstrumentation(), new KafkaProducerInstrumentation());
  }
}
