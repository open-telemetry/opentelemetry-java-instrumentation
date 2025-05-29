/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.metro;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.sun.xml.ws.api.pipe.ServerTubeAssemblerContext;
import com.sun.xml.ws.api.pipe.Tube;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ServerTubeAssemblerContextInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.sun.xml.ws.api.pipe.ServerTubeAssemblerContext");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("createMonitoringTube").and(takesArgument(0, named("com.sun.xml.ws.api.pipe.Tube"))),
        ServerTubeAssemblerContextInstrumentation.class.getName() + "$AddTracingAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddTracingAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ServerTubeAssemblerContext context,
        @Advice.Return(readOnly = false) Tube tube) {
      tube = new TracingTube(context.getEndpoint(), tube);
    }
  }
}
