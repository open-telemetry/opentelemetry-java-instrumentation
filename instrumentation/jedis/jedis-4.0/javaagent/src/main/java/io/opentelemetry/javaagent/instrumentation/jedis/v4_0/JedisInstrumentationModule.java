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
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import java.util.function.BiConsumer;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class JedisInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public JedisInstrumentationModule() {
    super("jedis", "jedis-4.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 4.0
    return hasClassesNamed("redis.clients.jedis.CommandArguments");
  }

  @Override
  public void registerVirtualFields(BiConsumer<String, String> virtualFieldRegistrar) {
    String configuredTarget = JedisSingletons.ConfiguredTarget.class.getName();
    virtualFieldRegistrar.accept(
        "redis.clients.jedis.providers.JedisConnectionProvider", configuredTarget);
    virtualFieldRegistrar.accept(
        "redis.clients.jedis.providers.ConnectionProvider", configuredTarget);
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
