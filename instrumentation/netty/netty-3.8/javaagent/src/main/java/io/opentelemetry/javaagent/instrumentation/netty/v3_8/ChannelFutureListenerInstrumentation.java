/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;

public class ChannelFutureListenerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.jboss.netty.channel.ChannelFutureListener");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.jboss.netty.channel.ChannelFutureListener"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("operationComplete"))
            .and(takesArgument(0, named("org.jboss.netty.channel.ChannelFuture"))),
        ChannelFutureListenerInstrumentation.class.getName() + "$OperationCompleteAdvice");
  }

  @SuppressWarnings("unused")
  public static class OperationCompleteAdvice {

    @Advice.OnMethodEnter
    public static Scope activateScope(@Advice.Argument(0) ChannelFuture future) {
      /*
      Idea here is:
       - To return scope only if we have captured it.
       - To capture scope only in case of error.
       */
      Throwable cause = future.getCause();
      if (cause == null || future.getChannel() == null) {
        return null;
      }

      VirtualField<Channel, NettyConnectionContext> virtualField =
          VirtualField.find(Channel.class, NettyConnectionContext.class);

      NettyConnectionContext connectionContext = virtualField.get(future.getChannel());
      if (connectionContext == null) {
        return null;
      }
      Context parentContext = connectionContext.get();
      if (parentContext == null) {
        return null;
      }
      return parentContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void deactivateScope(@Advice.Enter Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
