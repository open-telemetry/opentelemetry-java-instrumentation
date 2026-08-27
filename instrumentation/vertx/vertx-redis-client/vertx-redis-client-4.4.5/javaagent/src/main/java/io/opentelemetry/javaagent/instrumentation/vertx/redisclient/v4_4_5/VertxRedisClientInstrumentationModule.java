/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class VertxRedisClientInstrumentationModule extends InstrumentationModule {

  public VertxRedisClientInstrumentationModule() {
    super("vertx-redis-client", "vertx-redis-client-4.4.5", "vertx");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 4.4.5
    return hasClassesNamed("io.vertx.redis.client.RedisConnectOptions");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new RedisConnectionProviderInstrumentation(),
        new RedisStandaloneConnectionInstrumentation());
  }
}
