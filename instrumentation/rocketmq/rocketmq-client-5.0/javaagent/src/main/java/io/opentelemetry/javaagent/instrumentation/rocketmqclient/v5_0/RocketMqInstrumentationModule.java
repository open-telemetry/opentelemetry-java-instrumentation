/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RocketMqInstrumentationModule extends InstrumentationModule {
  public RocketMqInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "rocketmq-client-5.0" : "rocketmq-client",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"rocketmq-client"}
            : new String[] {"rocketmq-client-5.0"});
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new PublishingMessageImplInstrumentation(),
        new ProducerImplInstrumentation(),
        new ConsumerImplInstrumentation(),
        new ConsumeServiceInstrumentation(),
        new SimpleConsumerImplInstrumentation());
  }
}
