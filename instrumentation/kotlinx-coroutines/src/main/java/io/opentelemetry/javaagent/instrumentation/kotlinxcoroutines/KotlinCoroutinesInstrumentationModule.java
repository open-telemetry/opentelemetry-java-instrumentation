/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines;

import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function2;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@AutoService(InstrumentationModule.class)
public class KotlinCoroutinesInstrumentationModule extends InstrumentationModule {
  /*
  Kotlin coroutines with suspend functions are a form of cooperative "userland" threading
  (you might also know this pattern as "fibers" or "green threading", where the OS/kernel-level thread
  has no idea of switching between tasks.  Fortunately kotlin exposes hooks for the key events: knowing when
  coroutines are being created, when they are suspended (swapped out/inactive), and when they are resumed (about to
  run again).

  Without this instrumentation, heavy concurrency and usage of kotlin suspend functions will break causality
  and cause nonsensical span parents/context propagation.  This is because a single JVM thread will run a series of
  coroutines in an "arbitrary" order, and a context set by coroutine A (which then gets suspended) will be picked up
  by completely-unrelated coroutine B.

  The basic strategy here is:
  1) Use the DebugProbes callbacks to learn about coroutine create, resume, and suspend operations
  2) Wrap the creation Coroutine and its Context and use that wrapping to add an extra Context "key"
  3) Use the callback for resume and suspend to manipulate our context "key" whereby an appropriate state
     object can be found (tied to the chain of Continutations in the Coroutine).
  4) Do our swapping-context dance with that appropriate state
  5) Test with highly concurrent well-known span causality and ensure everything looks right.
     Without this instrumentation, this test fails with concurrency=2; with this instrumentation,
     it passes with concurrency=200.
  */

  public KotlinCoroutinesInstrumentationModule() {
    super("kotlinx-coroutines");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new KotlinDebugProbeInstrumentation());
  }

  private static final class KotlinDebugProbeInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return named("kotlin.coroutines.jvm.internal.DebugProbesKt");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      transformers.put(
          named("probeCoroutineCreated").and(takesArguments(1)),
          CoroutineCreatedAdvice.class.getName());
      transformers.put(
          named("probeCoroutineResumed").and(takesArguments(1)),
          CoroutineResumedAdvice.class.getName());
      transformers.put(
          named("probeCoroutineSuspended").and(takesArguments(1)),
          CoroutineSuspendedAdvice.class.getName());
      return transformers;
    }
  }

  public static class CoroutineCreatedAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(@Advice.Return(readOnly = false) Continuation<?> retVal) {
      if (!(retVal instanceof CoroutineWrapper)) {
        retVal = new CoroutineWrapper<>(retVal);
      }
    }
  }

  public static class CoroutineResumedAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(0) final Continuation<?> continuation) {
      CoroutineContextWrapper w = continuation.getContext().get(TraceScopeKey.INSTANCE);
      if (w != null) {
        w.tracingResume();
      }
    }
  }

  public static class CoroutineSuspendedAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(@Advice.Argument(0) final Continuation<?> continuation) {
      CoroutineContextWrapper w = continuation.getContext().get(TraceScopeKey.INSTANCE);
      if (w != null) {
        w.tracingSuspend();
      }
    }
  }

  public static class TraceScopeKey implements CoroutineContext.Key<CoroutineContextWrapper> {
    public static final TraceScopeKey INSTANCE = new TraceScopeKey();
  }

  public static class CoroutineWrapper<T> implements Continuation<T> {
    private final Continuation<T> proxy;
    private final CoroutineContextWrapper contextWrapper;

    public CoroutineWrapper(Continuation<T> proxy) {
      this.proxy = proxy;
      this.contextWrapper = new CoroutineContextWrapper(proxy.getContext());
    }

    @Override
    public String toString() {
      return proxy.toString();
    }

    @NotNull
    @Override
    public CoroutineContext getContext() {
      return contextWrapper;
    }

    @Override
    public void resumeWith(@NotNull Object o) {
      proxy.resumeWith(o);
    }
  }

  public static class CoroutineContextWrapper
      implements CoroutineContext, CoroutineContext.Element {
    private final CoroutineContext proxy;
    private Context myTracingContext;
    private Scope scope;

    public CoroutineContextWrapper(CoroutineContext proxy) {
      this.proxy = proxy;
      this.myTracingContext = Context.current();
    }

    @Override
    public <R> R fold(R r, @NotNull Function2<? super R, ? super Element, ? extends R> function2) {
      return proxy.fold(r, function2);
    }

    @Nullable
    @Override
    public <E extends Element> E get(@NotNull Key<E> key) {
      if (key == TraceScopeKey.INSTANCE) {
        return (E) this;
      }
      return proxy.get(key);
    }

    @NotNull
    @Override
    public CoroutineContext minusKey(@NotNull Key<?> key) {
      // I can't be removed!
      return proxy.minusKey(key);
    }

    @NotNull
    @Override
    public CoroutineContext plus(@NotNull CoroutineContext coroutineContext) {
      return proxy.plus(coroutineContext);
    }

    @Override
    public String toString() {
      return proxy.toString();
    }

    @NotNull
    @Override
    public Key<?> getKey() {
      return TraceScopeKey.INSTANCE;
    }

    // Actual tracing context-switch logic
    public void tracingSuspend() {
      // TODO(anuraaga): Investigate why test passes only with this call here. Conceptually it seems
      // weird to overwrite current context like this.
      myTracingContext = Context.current();
      scope.close();
    }

    public void tracingResume() {
      scope = myTracingContext.makeCurrent();
    }
  }
}
