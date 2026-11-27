/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0.cxf.v4_0;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.message.Message;

class CxfJaxRsUtilsInstrumentation implements TypeInstrumentation {

  private static final ElementMatcher.Junction<MethodDescription> PUSH_ONTO_STACK =
      isStatic()
          .and(named("pushOntoStack"))
          .and(
              takesArguments(
                  named("org.apache.cxf.jaxrs.model.OperationResourceInfo"),
                  named("jakarta.ws.rs.core.MultivaluedMap"),
                  named("org.apache.cxf.message.Message")));

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.cxf.jaxrs.utils.JAXRSUtils").and(declaresMethod(PUSH_ONTO_STACK));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(PUSH_ONTO_STACK, getClass().getName() + "$MatchAdvice");
  }

  @SuppressWarnings("unused")
  public static class MatchAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Argument(2) Message message) {
      // CXF records the match before post-matching filters and parameter conversion.
      OperationResourceInfoStack matchedResources = message.get(OperationResourceInfoStack.class);
      CxfSpanName.updateServerRoute(message, matchedResources);
    }
  }
}
