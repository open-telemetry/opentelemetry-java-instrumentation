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
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Cluster instrumentation is isolated from the core Jedis 2.x instrumentation so Muzzle can disable
 * it independently before the cluster classes were added in Jedis 2.3.
 */
@AutoService(InstrumentationModule.class)
public class JedisClusterInstrumentationModule extends InstrumentationModule {

  public JedisClusterInstrumentationModule() {
    super("jedis", "jedis-2.0", "jedis-2.3-cluster");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.3
    return hasClassesNamed("redis.clients.jedis.JedisClusterConnectionHandler")
        // added in 3.0
        .and(not(hasClassesNamed("redis.clients.jedis.commands.ProtocolCommand")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new JedisClusterInstrumentation(), new JedisClusterCommandInstrumentation());
  }
}
