/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.dubbo.remoting.exchange.Request;
import org.apache.dubbo.remoting.exchange.Response;

public class DubboNativeClientResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.dubbo.remoting.exchange.support.DefaultFuture");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("newFuture")
            .and(takesArgument(1, named("org.apache.dubbo.remoting.exchange.Request"))),
        getClass().getName() + "$NewFutureAdvice");
    transformer.applyAdviceToMethod(
        named("doReceived")
            .and(takesArgument(0, named("org.apache.dubbo.remoting.exchange.Response"))),
        getClass().getName() + "$DoReceivedAdvice");
  }

  @SuppressWarnings("unused")
  public static class NewFutureAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(1) Request request) {
      DubboNativeResponseStatusAccessor.bindClientRequest(request);
    }
  }

  @SuppressWarnings("unused")
  public static class DoReceivedAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.This Object future, @Advice.Argument(0) Response response) {
      DubboNativeResponseStatusAccessor.captureClientStatus(future, response);
    }
  }
}
