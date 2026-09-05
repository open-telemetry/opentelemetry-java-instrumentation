/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilder;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JedisInstrumentationModule extends InstrumentationModule {

  public JedisInstrumentationModule() {
    super("jedis", "jedis-4.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 4.0
    return hasClassesNamed("redis.clients.jedis.CommandArguments");
  }

  public void registerMuzzleVirtualFields(VirtualFieldMappingsBuilder builder) {
    String configuredTarget =
        "io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons$ConfiguredTarget";
    builder
        .register(
            "redis.clients.jedis.Connection",
            "io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisConnectionInfo")
        .register(
            "redis.clients.jedis.Connection",
            "io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget")
        .register("redis.clients.jedis.Connection", "java.lang.Boolean")
        .register(
            "redis.clients.jedis.util.Pool",
            "io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget")
        .register("redis.clients.jedis.util.Pool", "java.lang.Boolean")
        .register("redis.clients.jedis.providers.JedisConnectionProvider", configuredTarget)
        .register("redis.clients.jedis.providers.ConnectionProvider", configuredTarget)
        .register("redis.clients.jedis.JedisClusterInfoCache", configuredTarget)
        .register("redis.clients.jedis.Pipeline", "java.util.List")
        .register("redis.clients.jedis.Transaction", "java.util.List")
        .register("redis.clients.jedis.JedisSocketFactory", "redis.clients.jedis.HostAndPort");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new DefaultJedisSocketFactoryInstrumentation(),
        new JedisConnectionInstrumentation(),
        new JedisConnectionProviderInstrumentation(),
        new JedisSentinelPoolInstrumentation(),
        new PoolResourceInstrumentation(),
        new JedisInstrumentation(),
        new JedisPipelineInstrumentation(),
        new JedisTransactionInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return "redis.clients.jedis.DefaultJedisSocketFactoryUtil".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("redis.clients.jedis.DefaultJedisSocketFactoryUtil");
  }
}
