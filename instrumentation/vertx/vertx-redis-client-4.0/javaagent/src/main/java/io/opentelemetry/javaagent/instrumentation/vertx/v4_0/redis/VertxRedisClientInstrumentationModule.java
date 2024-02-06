/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.redis;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class VertxRedisClientInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public VertxRedisClientInstrumentationModule() {
    super("vertx-redis-client", "vertx-redis-client-4.0", "vertx");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "io.vertx.redis.client.impl.RequestUtil".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("io.vertx.redis.client.impl.RequestUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RedisStandaloneConnectionInstrumentation(),
        new RedisConnectionProviderInstrumentation(),
        new CommandImplInstrumentation());
  }
}
