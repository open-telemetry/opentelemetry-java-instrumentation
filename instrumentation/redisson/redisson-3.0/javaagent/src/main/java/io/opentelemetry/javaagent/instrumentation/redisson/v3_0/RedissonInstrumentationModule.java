/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonInstrumentationModule extends InstrumentationModule {

  public RedissonInstrumentationModule() {
    super("redisson", "redisson-3.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 3.17.0
    return not(hasClassesNamed("org.redisson.api.RFunction"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ConnectionManagerConnectionFutureInstrumentation(),
        new ConnectionManagerInstrumentation(),
        new RedisConnectionInstrumentation(),
        new RedisCommandDataInstrumentation());
  }

  @Override
  public boolean isHelperClass(String className) {
    return "org.redisson.config.ConfigServerTargetsBefore317".equals(className);
  }

  @Override
  public List<String> injectedClassNames() {
    return singletonList("org.redisson.config.ConfigServerTargetsBefore317");
  }
}
