/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.kotlincoroutines;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.HashMap;
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

@AutoService(Instrumenter.class)
public class KotlinProbeInstrumentation extends Instrumenter.Default {
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

  public KotlinProbeInstrumentation() {
    super("kotlin-coroutines");
  }

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

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.kotlincoroutines.KotlinProbeInstrumentation$CoroutineWrapper",
      "io.opentelemetry.instrumentation.auto.kotlincoroutines.KotlinProbeInstrumentation$TraceScopeKey",
      "io.opentelemetry.instrumentation.auto.kotlincoroutines.KotlinProbeInstrumentation$CoroutineContextWrapper",
    };
  }

  public static class CoroutineCreatedAdvice {
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void exit(
        @Advice.Return(readOnly = false) kotlin.coroutines.Continuation retVal) {
      if (!(retVal instanceof CoroutineWrapper)) {
        retVal = new CoroutineWrapper(retVal);
      }
    }
  }

  public static class CoroutineResumedAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(0) final kotlin.coroutines.Continuation continuation) {
      continuation.getContext().get(TraceScopeKey.INSTANCE);
    }
  }

  public static class CoroutineSuspendedAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void enter(
        @Advice.Argument(0) final kotlin.coroutines.Continuation continuation) {
      continuation.getContext().minusKey(TraceScopeKey.INSTANCE);
    }
  }

  public static class TraceScopeKey implements CoroutineContext.Key {
    public static final TraceScopeKey INSTANCE = new TraceScopeKey();
  }

  public static class CoroutineWrapper implements kotlin.coroutines.Continuation {
    private final Continuation proxy;
    private final CoroutineContextWrapper contextWrapper;

    public CoroutineWrapper(Continuation proxy) {
      this.proxy = proxy;
      this.contextWrapper = new CoroutineContextWrapper(proxy.getContext());
    }

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

  public static class CoroutineContextWrapper implements CoroutineContext {
    private final CoroutineContext proxy;
    private Context myTracingContext;
    private Context prevTracingContext;

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
        prevTracingContext = myTracingContext.attach();
      }
      return proxy.get(key);
    }

    @NotNull
    @Override
    public CoroutineContext minusKey(@NotNull Key<?> key) {
      if (key == TraceScopeKey.INSTANCE) {
        myTracingContext = Context.current();
        myTracingContext.detach(prevTracingContext);
      }
      return proxy.minusKey(key);
    }

    @NotNull
    @Override
    public CoroutineContext plus(@NotNull CoroutineContext coroutineContext) {
      return proxy.plus(coroutineContext);
    }

    public String toString() {
      return proxy.toString();
    }
  }
}
