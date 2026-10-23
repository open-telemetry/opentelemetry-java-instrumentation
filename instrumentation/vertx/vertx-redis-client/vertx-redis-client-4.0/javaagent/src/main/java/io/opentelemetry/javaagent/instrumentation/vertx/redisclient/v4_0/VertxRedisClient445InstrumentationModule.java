/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxRedisClient445InstrumentationModule extends InstrumentationModule {

  public VertxRedisClient445InstrumentationModule() {
    super("vertx-redis-client-4.0", "vertx-redis-client", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 4.4.5
    return hasClassesNamed("io.vertx.redis.client.RedisConnectOptions");
  }

  @Override
  public boolean isHelperClass(String className) {
    return className.startsWith("io.vertx.redis.client.impl.RedisConnectionManagerUtil");
  }

  @Override
  public List<String> injectedClassNames() {
    return asList(
        "io.vertx.redis.client.impl.RedisConnectionManagerUtil",
        "io.vertx.redis.client.impl.RedisConnectionManagerUtil$CapturedTarget");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new Redis445ConnectionManagerInstrumentation(),
        new Redis445ConnectionProviderInstrumentation());
  }
}
