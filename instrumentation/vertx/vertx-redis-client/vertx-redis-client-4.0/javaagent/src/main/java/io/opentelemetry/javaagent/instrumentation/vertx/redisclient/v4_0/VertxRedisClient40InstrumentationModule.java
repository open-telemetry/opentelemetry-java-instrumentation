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
public class VertxRedisClient40InstrumentationModule extends InstrumentationModule {

  public VertxRedisClient40InstrumentationModule() {
    super("vertx-redis-client-4.0", "vertx-redis-client", "vertx");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.equals("io.vertx.redis.client.impl.RequestUtil")
        || className.startsWith("io.vertx.redis.client.impl.RedisConnectionManagerUtil");
  }

  @Override
  public List<String> injectedClassNames() {
    return asList(
        "io.vertx.redis.client.impl.RequestUtil",
        "io.vertx.redis.client.impl.RedisConnectionManagerUtil",
        "io.vertx.redis.client.impl.RedisConnectionManagerUtil$CapturedTarget");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RedisStandaloneConnectionInstrumentation(),
        new RespParserInstrumentation(),
        new RedisConnectionManagerInstrumentation(),
        new RedisConnectionProviderInstrumentation(),
        new CommandImplInstrumentation());
  }
}
