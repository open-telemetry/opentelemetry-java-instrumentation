/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;

import akka.stream.impl.fusing.GraphInterpreter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class GraphInterpreterInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("akka.http.scaladsl.HttpExt");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.stream.impl.fusing.GraphInterpreter");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(named("processPush"), getClass().getName() + "$PushAdvice");
  }

  @SuppressWarnings("unused")
  public static class PushAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Nullable
    public static Scope onEnter(@Advice.Argument(0) GraphInterpreter.Connection connection) {
      // processPush is called when execution passes to application or server. Here we propagate the
      // context to the application code.
      Context context = AkkaFlowWrapper.getContext(connection.outHandler());
      if (context != null) {
        return context.makeCurrent();
      }
      return null;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
