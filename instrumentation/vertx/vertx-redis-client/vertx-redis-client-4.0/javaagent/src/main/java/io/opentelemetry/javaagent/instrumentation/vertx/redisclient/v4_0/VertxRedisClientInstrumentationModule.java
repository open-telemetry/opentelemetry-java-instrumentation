/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class VertxRedisClientInstrumentationModule extends InstrumentationModule {

  public VertxRedisClientInstrumentationModule() {
    super("vertx-redis-client", "vertx-redis-client-4.0", "vertx");
  }

  @Override
  public boolean isHelperClass(String className) {
    return "io.vertx.redis.client.impl.RequestUtil".equals(className)
        || "io.vertx.redis.client.impl.RedisConnectionManagerUtil".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return asList(
        "io.vertx.redis.client.impl.RequestUtil",
        "io.vertx.redis.client.impl.RedisConnectionManagerUtil");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RedisStandaloneConnectionInstrumentation(),
        new RedisConnectionManagerInstrumentation(),
        new RedisConnectionProviderInstrumentation(),
        new CommandImplInstrumentation());
  }
}
