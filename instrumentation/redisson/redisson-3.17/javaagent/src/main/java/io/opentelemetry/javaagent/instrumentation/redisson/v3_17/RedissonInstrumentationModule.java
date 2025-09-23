/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public RedissonInstrumentationModule() {
    super("redisson", "redisson-3.17");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("org.redisson.api.RFunction");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RedisConnectionInstrumentation(), new RedisCommandDataInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
