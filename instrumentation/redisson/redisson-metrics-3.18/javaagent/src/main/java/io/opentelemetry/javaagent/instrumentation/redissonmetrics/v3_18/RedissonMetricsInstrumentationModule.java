/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_18;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedisClientInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonMetricsInstrumentationModule extends InstrumentationModule {

  public RedissonMetricsInstrumentationModule() {
    super("redisson-metrics", "redisson-metrics-3.18");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.18.0
    return hasClassesNamed("org.redisson.misc.AsyncSemaphore")
        // added in 3.26.0
        .and(not(hasClassesNamed("org.redisson.connection.ConnectionsHolder")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientConnectionsEntryInstrumentation(), new RedisClientInstrumentation());
  }
}
