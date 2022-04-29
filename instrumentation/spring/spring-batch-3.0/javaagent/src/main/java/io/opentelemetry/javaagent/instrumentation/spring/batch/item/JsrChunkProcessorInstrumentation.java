/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldTraceItems;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.ITEM_OPERATION_PROCESS;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.ITEM_OPERATION_READ;
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

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      Context parentContext = currentContext();
      ChunkContext chunkContext = getChunkContext(parentContext);
      if (chunkContext == null || !shouldTraceItems()) {
        return;
      }

      item = ItemSingletons.itemName(chunkContext, ITEM_OPERATION_READ);
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
  public static class TransformAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
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
  public static class PersistAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      Context parentContext = currentContext();
      ChunkContext chunkContext = getChunkContext(parentContext);
      if (chunkContext == null || !shouldTraceItems()) {
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
