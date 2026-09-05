/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JedisInstrumentationModule extends InstrumentationModule {

  public JedisInstrumentationModule() {
    super("jedis", "jedis-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.0
    return hasClassesNamed("redis.clients.jedis.Response")
        // added in 3.0
        .and(not(hasClassesNamed("redis.clients.jedis.commands.ProtocolCommand")));
  }

  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    builder
        .register(
            "redis.clients.jedis.Connection",
            "io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget")
        .register("redis.clients.jedis.Connection", "java.lang.Boolean")
        .register(
            "redis.clients.util.Sharded",
            "io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget")
        .register("redis.clients.util.Sharded", "java.lang.Boolean")
        .register(
            "redis.clients.util.Pool",
            "io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget")
        .register("redis.clients.util.Pool", "java.lang.Boolean")
        .register("redis.clients.jedis.Queable", "java.util.List")
        .register(
            "redis.clients.jedis.JedisClusterConnectionHandler",
            "io.opentelemetry.javaagent.instrumentation.jedis.v2_0.JedisSingletons$ConfiguredTarget");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new JedisConnectionInstrumentation(),
        new ShardedJedisInstrumentation(),
        new ShardedRoutingInstrumentation(),
        new JedisSentinelPoolInstrumentation(),
        new PoolResourceInstrumentation(),
        new JedisClusterInstrumentation(),
        new JedisInstrumentation(),
        new JedisPipelineInstrumentation(),
        new JedisTransactionInstrumentation());
  }
}
