/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class SpringKafkaInstrumentationModule extends InstrumentationModule {
  public SpringKafkaInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "spring-kafka-2.7" : "spring-kafka",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"spring-kafka"}
            : new String[] {"spring-kafka-2.7"});
  }

  @Override
  public List<String> getAdditionalHelperClassNames() {
    // ThreadState is used only by callbacks added after the minimum supported Spring version.
    return singletonList(
        "io.opentelemetry.instrumentation.spring.kafka.v2_7.InstrumentedRecordInterceptor$ThreadState");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new AbstractMessageListenerContainerInstrumentation(),
        new ListenerConsumerInstrumentation());
  }
}
