/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_7;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.channel.Channel;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.ContextHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.exec.Downstream;
import ratpack.exec.Execution;

public class RequestActionSupportInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("ratpack.http.client.internal.RequestActionSupport"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPrivate())
            .and(named("send"))
            .and(takesArgument(0, named("ratpack.exec.Downstream")))
            .and(takesArgument(1, named("io.netty.channel.Channel"))),
        RequestActionSupportInstrumentation.class.getName() + "$SendAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("connect")).and(takesArgument(0, named("ratpack.exec.Downstream"))),
        RequestActionSupportInstrumentation.class.getName() + "$ConnectDownstreamAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("connect")).and(takesArgument(0, named("ratpack.exec.Downstream"))),
        RequestActionSupportInstrumentation.class.getName() + "$ContextAdvice");
  }

  @SuppressWarnings("unused")
  public static class SendAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void injectChannelAttribute(
        @Advice.FieldValue("execution") Execution execution, @Advice.Argument(1) Channel channel) {
      RatpackSingletons.propagateContextToChannel(execution, channel);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectDownstreamAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    @Advice.AssignReturned.ToArguments(@ToArgument(0))
    public static Object wrapDownstream(@Advice.Argument(0) Downstream<?> downstream) {
      // Propagate the current context to downstream
      // since that is the subsequent code chained to the http client call
      return DownstreamWrapper.wrapIfNeeded(downstream);
    }
  }

  @SuppressWarnings("unused")
  public static class ContextAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope injectChannelAttribute(
        @Advice.FieldValue("execution") Execution execution) {

      // Capture the CLIENT span and make it current before calling Netty layer
      return execution
          .maybeGet(ContextHolder.class)
          .map(ContextHolder::context)
          .map(Context::makeCurrent)
          .orElse(null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
