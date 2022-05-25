/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17_2;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Arrays.asList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RedissonInstrumentationModule extends InstrumentationModule {

  public RedissonInstrumentationModule() {
    super("redisson", "redisson-3.17.2");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return not(hasClassesNamed("org.redisson.misc.RPromise"));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(new RedisConnectionInstrumentation(), new RedisCommandDataInstrumentation());
  }
}
