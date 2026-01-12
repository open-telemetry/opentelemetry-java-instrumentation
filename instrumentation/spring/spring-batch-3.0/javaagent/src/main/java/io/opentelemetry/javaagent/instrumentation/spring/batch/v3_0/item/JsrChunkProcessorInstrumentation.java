/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.ITEM_OPERATION_PROCESS;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.ITEM_OPERATION_READ;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.ITEM_OPERATION_WRITE;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.AdviceScope;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JsrChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.jsr.step.item.JsrChunkProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isProtected().and(named("doProvide")).and(takesArguments(2)),
        this.getClass().getName() + "$ProvideAdvice");
    transformer.applyAdviceToMethod(
        isProtected().and(named("doTransform")).and(takesArguments(1)),
        this.getClass().getName() + "$TransformAdvice");
    transformer.applyAdviceToMethod(
        isProtected().and(named("doPersist")).and(takesArguments(2)),
        this.getClass().getName() + "$PersistAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProvideAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter() {
      return AdviceScope.enter(ITEM_OPERATION_READ);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(thrown);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class TransformAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter() {
      return AdviceScope.enter(ITEM_OPERATION_PROCESS);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(thrown);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class PersistAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter() {
      return AdviceScope.enter(ITEM_OPERATION_WRITE);
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable thrown,
        @Advice.Enter @Nullable AdviceScope adviceScope) {
      if (adviceScope != null) {
        adviceScope.exit(thrown);
      }
    }
  }
}
