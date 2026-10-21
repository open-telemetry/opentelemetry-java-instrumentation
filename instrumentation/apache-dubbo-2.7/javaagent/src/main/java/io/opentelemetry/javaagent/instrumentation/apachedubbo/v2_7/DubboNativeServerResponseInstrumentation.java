/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.remoting.exchange.Response;

public class DubboNativeServerResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.remoting.exchange.Response");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, long.class)),
        getClass().getName() + "$ResponseConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("setStatus").and(takesArgument(0, byte.class)),
        getClass().getName() + "$SetStatusAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResponseConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Response response) {
      DubboNativeResponseStatusAccessor.associateServerResponse(response);
    }
  }

  @SuppressWarnings("unused")
  public static class SetStatusAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.This Response response) {
      DubboNativeResponseStatusAccessor.captureServerStatus(response);
    }
  }
}
