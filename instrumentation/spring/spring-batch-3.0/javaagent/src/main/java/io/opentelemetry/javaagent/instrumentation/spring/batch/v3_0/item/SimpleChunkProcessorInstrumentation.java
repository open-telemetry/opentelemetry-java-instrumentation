/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.ITEM_OPERATION_PROCESS;
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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;

public class SimpleChunkProcessorInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.step.item.SimpleChunkProcessor");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isProtected().and(named("doProcess")).and(takesArguments(1)),
        this.getClass().getName() + "$ProcessAdvice");
    transformer.applyAdviceToMethod(
        isProtected().and(named("doWrite")).and(takesArguments(1)),
        this.getClass().getName() + "$WriteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ProcessAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(
        @Advice.FieldValue("itemProcessor") ItemProcessor<?, ?> itemProcessor) {
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
  public static class WriteAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AdviceScope onEnter(@Advice.FieldValue("itemWriter") ItemWriter<?> itemWriter) {
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
