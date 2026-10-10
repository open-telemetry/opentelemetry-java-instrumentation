/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedisClientInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonMetricsInstrumentationModule extends InstrumentationModule {

  public RedissonMetricsInstrumentationModule() {
    super(
        AgentCommonConfig.get().isV3Preview() ? "redisson-metrics-3.26" : "redisson-metrics",
        AgentCommonConfig.get().isV3Preview()
            ? new String[] {"redisson-metrics", "redisson"}
            : new String[] {"redisson-metrics-3.26"});
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.26.0
    return hasClassesNamed("org.redisson.connection.ConnectionsHolder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientConnectionsEntryInstrumentation(), new RedisClientInstrumentation());
  }
}
