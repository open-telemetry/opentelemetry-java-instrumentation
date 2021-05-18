/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4_1;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isArray;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
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

  public static class AddListenerAdvice {
    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<? super Void>> listener) {
      ContextStore<GenericFutureListener, GenericFutureListener> contextStore =
          InstrumentationContext.get(GenericFutureListener.class, GenericFutureListener.class);
      listener =
          FutureListenerWrappers.wrap(contextStore, Java8BytecodeBridge.currentContext(), listener);
    }
  }

  public static class AddListenersAdvice {
    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<? super Void>>[] listeners) {

      ContextStore<GenericFutureListener, GenericFutureListener> contextStore =
          InstrumentationContext.get(GenericFutureListener.class, GenericFutureListener.class);
      Context context = Java8BytecodeBridge.currentContext();
      @SuppressWarnings("unchecked")
      GenericFutureListener<? extends Future<? super Void>>[] wrappedListeners =
          new GenericFutureListener[listeners.length];
      for (int i = 0; i < listeners.length; ++i) {
        wrappedListeners[i] = FutureListenerWrappers.wrap(contextStore, context, listeners[i]);
      }
      listeners = wrappedListeners;
    }
  }

  public static class RemoveListenerAdvice {
    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<? super Void>> listener) {
      ContextStore<GenericFutureListener, GenericFutureListener> contextStore =
          InstrumentationContext.get(GenericFutureListener.class, GenericFutureListener.class);
      listener = FutureListenerWrappers.getWrapper(contextStore, listener);
    }
  }

  public static class RemoveListenersAdvice {
    @Advice.OnMethodEnter
    public static void wrapListener(
        @Advice.Argument(value = 0, readOnly = false)
            GenericFutureListener<? extends Future<? super Void>>[] listeners) {

      ContextStore<GenericFutureListener, GenericFutureListener> contextStore =
          InstrumentationContext.get(GenericFutureListener.class, GenericFutureListener.class);
      @SuppressWarnings("unchecked")
      GenericFutureListener<? extends Future<? super Void>>[] wrappedListeners =
          new GenericFutureListener[listeners.length];
      for (int i = 0; i < listeners.length; ++i) {
        wrappedListeners[i] = FutureListenerWrappers.getWrapper(contextStore, listeners[i]);
      }
      listeners = wrappedListeners;
    }
  }
}
