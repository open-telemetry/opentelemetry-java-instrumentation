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
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import java.util.function.BiConsumer;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JedisInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

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

  @Override
  public void registerVirtualFields(BiConsumer<String, String> virtualFieldRegistrar) {
    virtualFieldRegistrar.accept(
        "redis.clients.jedis.JedisClusterConnectionHandler",
        JedisSingletons.ConfiguredTarget.class.getName());
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
