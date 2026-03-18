/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql.v12_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

@SuppressWarnings("unused")
@AutoService(InstrumentationModule.class)
public class GraphqlInstrumentationModule extends InstrumentationModule
    implements ExperimentalInstrumentationModule {

  public GraphqlInstrumentationModule() {
    super("graphql-java", "graphql-java-12.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // graphql.GraphQL has been present since version 1.0
    return hasClassesNamed("graphql.GraphQL")
        // added in 20.0
        .and(
            not(
                hasClassesNamed(
                    "graphql.execution.instrumentation.SimplePerformantInstrumentation")));
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new GraphqlInstrumentation());
  }

  @Override
  public boolean isIndyReady() {
    return true;
  }
}
