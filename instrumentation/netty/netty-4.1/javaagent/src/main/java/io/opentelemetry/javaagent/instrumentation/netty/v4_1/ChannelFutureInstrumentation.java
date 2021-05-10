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
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ChannelFutureInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("io.netty.channel.ChannelFuture");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("io.netty.channel.ChannelFuture"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher.Junction<MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        isMethod()
            .and(named("addListener"))
            .and(takesArgument(0, named("io.netty.util.concurrent.GenericFutureListener"))),
        ChannelFutureInstrumentation.class.getName() + "$AddListenerAdvice");
    transformers.put(
        isMethod().and(named("addListeners")).and(takesArgument(0, isArray())),
        ChannelFutureInstrumentation.class.getName() + "$AddListenersAdvice");
    transformers.put(
        isMethod()
            .and(named("removeListener"))
            .and(takesArgument(0, named("io.netty.util.concurrent.GenericFutureListener"))),
        ChannelFutureInstrumentation.class.getName() + "$RemoveListenerAdvice");
    transformers.put(
        isMethod().and(named("removeListeners")).and(takesArgument(0, isArray())),
        ChannelFutureInstrumentation.class.getName() + "$RemoveListenersAdvice");
    return transformers;
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
