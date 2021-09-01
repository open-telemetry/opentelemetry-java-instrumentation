/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldTraceItems;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.ITEM_OPERATION_PROCESS;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.ITEM_OPERATION_WRITE;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.getChunkContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.itemInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.batch.core.scope.context.ChunkContext;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue("itemProcessor") ItemProcessor<?, ?> itemProcessor,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      Context parentContext = currentContext();
      ChunkContext chunkContext = getChunkContext(parentContext);
      if (chunkContext == null || !shouldTraceItems()) {
        return;
      }

      item = ItemSingletons.itemName(chunkContext, ITEM_OPERATION_PROCESS);
      if (!itemInstrumenter().shouldStart(parentContext, item)) {
        return;
      }

      context = itemInstrumenter().start(parentContext, item);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable thrown,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      if (scope == null) {
        return;
      }

      scope.close();
      itemInstrumenter().end(context, item, null, thrown);
    }
  }

  @SuppressWarnings("unused")
  public static class WriteAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue("itemWriter") ItemWriter<?> itemWriter,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      Context parentContext = currentContext();
      ChunkContext chunkContext = getChunkContext(parentContext);
      if (chunkContext == null || itemWriter == null || !shouldTraceItems()) {
        return;
      }

      item = ItemSingletons.itemName(chunkContext, ITEM_OPERATION_WRITE);
      if (!itemInstrumenter().shouldStart(parentContext, item)) {
        return;
      }

      context = itemInstrumenter().start(parentContext, item);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable thrown,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      if (scope == null) {
        return;
      }

      scope.close();
      itemInstrumenter().end(context, item, null, thrown);
    }
  }
}
