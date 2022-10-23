/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v4_8;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RocketMqInstrumentationModule extends InstrumentationModule {
  public RocketMqInstrumentationModule() {
    super("rocketmq-client", "rocketmq-client-4.8");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RocketMqProducerInstrumentation(), new RocketMqConsumerInstrumentation());
  }
}
