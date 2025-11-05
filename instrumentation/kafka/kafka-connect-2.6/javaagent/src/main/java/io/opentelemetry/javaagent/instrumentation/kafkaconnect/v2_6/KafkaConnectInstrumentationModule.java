/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class KafkaConnectInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public KafkaConnectInstrumentationModule() {
    super("kafka-connect", "kafka-connect-2.6");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new SinkTaskInstrumentation(), new WorkerSinkTaskInstrumentation());
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in 2.6.0
    return hasClassesNamed("org.apache.kafka.connect.sink.SinkConnectorContext");
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
