/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isArray;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class NettyFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.util.concurrent.Future");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.netty.util.concurrent.Future"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("addListener"))
            .and(takesArgument(0, named("io.netty.util.concurrent.GenericFutureListener"))),
        NettyFutureInstrumentation.class.getName() + "$AddListenerAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("addListeners")).and(takesArgument(0, isArray())),
        NettyFutureInstrumentation.class.getName() + "$AddListenersAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("removeListener"))
            .and(takesArgument(0, named("io.netty.util.concurrent.GenericFutureListener"))),
        NettyFutureInstrumentation.class.getName() + "$RemoveListenerAdvice");
    transformer.applyAdviceToMethod(
        isMethod().and(named("removeListeners")).and(takesArgument(0, isArray())),
        NettyFutureInstrumentation.class.getName() + "$RemoveListenersAdvice");
  }

  @SuppressWarnings("unused")
  public static class AddListenerAdvice {

    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<?>> listener) {
      // wrapping our "end" listener leads to strict context leak failures since there will be an
      // active scope when we call "end"
      // wrapping internal netty listeners also leads to strict context leak failures since some of
      // those are called after our "end" listener
      String listenerClassName = listener.getClass().getName();
      if (!listenerClassName.startsWith("io.opentelemetry.javaagent.")
          && !listenerClassName.startsWith("io.netty.")) {
        listener = FutureListenerWrappers.wrap(Java8BytecodeBridge.currentContext(), listener);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class AddListenersAdvice {

    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<?>>[] listeners) {

      Context context = Java8BytecodeBridge.currentContext();
      @SuppressWarnings("unchecked")
      GenericFutureListener<? extends Future<?>>[] wrappedListeners =
          new GenericFutureListener[listeners.length];
      for (int i = 0; i < listeners.length; ++i) {
        wrappedListeners[i] = FutureListenerWrappers.wrap(context, listeners[i]);
      }
      listeners = wrappedListeners;
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveListenerAdvice {

    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<?>> listener) {
      listener = FutureListenerWrappers.getWrapper(listener);
    }
  }

  @SuppressWarnings("unused")
  public static class RemoveListenersAdvice {

    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<?>>[] listeners) {

      @SuppressWarnings("unchecked")
      GenericFutureListener<? extends Future<?>>[] wrappedListeners =
          new GenericFutureListener[listeners.length];
      for (int i = 0; i < listeners.length; ++i) {
        wrappedListeners[i] = FutureListenerWrappers.getWrapper(listeners[i]);
      }
      listeners = wrappedListeners;
    }
  }
}
