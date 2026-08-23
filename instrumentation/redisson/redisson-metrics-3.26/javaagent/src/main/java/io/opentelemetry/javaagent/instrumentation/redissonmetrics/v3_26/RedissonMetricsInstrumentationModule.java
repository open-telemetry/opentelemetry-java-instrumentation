/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class RedissonMetricsInstrumentationModule extends InstrumentationModule {

  public RedissonMetricsInstrumentationModule() {
    super("redisson-metrics", "redisson-metrics-3.26");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientConnectionsEntryInstrumentation(), new RedisClientInstrumentation());
  }
}
