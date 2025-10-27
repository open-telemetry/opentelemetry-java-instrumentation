/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.context.server;

import static io.opentelemetry.javaagent.instrumentation.rmi.context.ContextPropagator.CONTEXT_CALL_ID;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import sun.rmi.transport.Target;

public class RmiServerContextInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("sun.rmi.transport.ObjectTable");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isStatic())
            .and(named("getTarget"))
            .and(takesArgument(0, named("sun.rmi.transport.ObjectEndpoint"))),
        getClass().getName() + "$ObjectTableAdvice");
  }

  @SuppressWarnings("unused")
  public static class ObjectTableAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Target methodExit(@Advice.Argument(0) Object oe, @Advice.Return Target result) {
      // comparing toString() output allows us to avoid using reflection to be able to compare
      // ObjID and ObjectEndpoint objects
      // ObjectEndpoint#toString() only returns this.objId.toString() value which is exactly
      // what we're interested in here.
      if (!CONTEXT_CALL_ID.toString().equals(oe.toString())) {
        return result;
      }
      return ContextDispatcher.newDispatcherTarget();
    }
  }
}
