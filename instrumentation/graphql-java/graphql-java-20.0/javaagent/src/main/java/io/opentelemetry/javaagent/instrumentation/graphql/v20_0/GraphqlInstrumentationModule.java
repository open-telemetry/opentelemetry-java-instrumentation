/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v20_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.Collections;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class GraphqlInstrumentationModule extends InstrumentationModule {

  public GraphqlInstrumentationModule() {
    super("graphql-java", "graphql-java-20.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // added in 20.0
    return hasClassesNamed("graphql.execution.instrumentation.SimplePerformantInstrumentation");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return Collections.singletonList(new GraphqlInstrumentation());
  }
}
