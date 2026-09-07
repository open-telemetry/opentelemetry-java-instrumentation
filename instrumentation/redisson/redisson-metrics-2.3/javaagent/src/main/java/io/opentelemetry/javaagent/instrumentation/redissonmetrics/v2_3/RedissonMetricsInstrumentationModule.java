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
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedisClientInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonMetricsInstrumentationModule extends InstrumentationModule {

  public RedissonMetricsInstrumentationModule() {
    super("redisson-metrics", "redisson-metrics-2.3");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed(
        // added in 2.3.0
        "org.redisson.api.RFuture",
        // removed in 3.18.0 (moved to org.redisson.misc)
        "org.redisson.pubsub.AsyncSemaphore");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new ClientConnectionsEntryInstrumentation(), new RedisClientInstrumentation());
  }
}
