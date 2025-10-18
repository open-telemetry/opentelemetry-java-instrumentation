/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.redis;

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
    super("vertx-redis-client", "vertx-redis-client-3.9", "vertx");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "io.vertx.redis.client.impl.RequestUtil39".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("io.vertx.redis.client.impl.RequestUtil39");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RedisClientInstrumentation(),
        new RedisClusterConnectionInstrumentation(),
        new RedisConnectionInstrumentation(),
        new RedisClientFactoryInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
