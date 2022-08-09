/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.graphql;

import static io.opentelemetry.javaagent.instrumentation.graphql.GraphqlSingletons.addInstrumentation;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import graphql.execution.instrumentation.Instrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GraphqlInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("graphql.GraphQL");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("checkInstrumentationDefaultState", "checkInstrumentation")
            .and(returns(named("graphql.execution.instrumentation.Instrumentation"))),
        this.getClass().getName() + "$AddInstrumentationAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddInstrumentationAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Return(readOnly = false) Instrumentation instrumentation) {
      instrumentation = addInstrumentation(instrumentation);
    }
  }
}
