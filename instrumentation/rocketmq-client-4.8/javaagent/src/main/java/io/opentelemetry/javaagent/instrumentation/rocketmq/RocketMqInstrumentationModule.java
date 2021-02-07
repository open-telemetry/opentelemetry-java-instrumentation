/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmq;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RocketMqInstrumentationModule extends InstrumentationModule {
  public RocketMqInstrumentationModule() {
    super("rocketmq", "rockemq-client-4.3");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RocketMqClientApiImplInstrumentation(),
        new RocketMqConcurrentlyConsumeInstrumentation(),
        new RocketMqOrderlyConsumeInstrumentation());
  }
}
