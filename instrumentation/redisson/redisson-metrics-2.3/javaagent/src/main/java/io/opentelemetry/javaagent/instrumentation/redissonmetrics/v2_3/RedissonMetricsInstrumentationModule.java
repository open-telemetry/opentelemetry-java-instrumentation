/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonMetricsInstrumentationModule extends InstrumentationModule {

  public RedissonMetricsInstrumentationModule() {
    super("redisson-metrics", "redisson-metrics-2.3");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 2.3.0, removed in 3.18.0 (moved to org.redisson.misc)
    return hasClassesNamed("org.redisson.pubsub.AsyncSemaphore");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientConnectionsEntryInstrumentation(), new RedisClientInstrumentation());
  }
}
