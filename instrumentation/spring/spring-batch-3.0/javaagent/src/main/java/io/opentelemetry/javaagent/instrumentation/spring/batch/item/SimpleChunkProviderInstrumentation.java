/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.item;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.SpringBatchInstrumentationConfig.shouldTraceItems;
import static io.opentelemetry.javaagent.instrumentation.spring.batch.item.ItemSingletons.ITEM_OPERATION_READ;
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

// item read instrumentation *cannot* use ItemReadListener: sometimes afterRead() is not called
// after beforeRead(), using listener here would cause unfinished spans/scopes
public class SimpleChunkProviderInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.batch.core.step.item.SimpleChunkProvider");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isProtected().and(named("doRead")).and(takesArguments(0)),
        this.getClass().getName() + "$ReadAdvice");
  }

  @SuppressWarnings("unused")
  public static class ReadAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Local("otelItem") String item) {
      ChunkContext chunkContext = getChunkContext(currentContext());
      if (chunkContext == null || !shouldTraceItems()) {
        return;
      }

      Context parentContext = currentContext();
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
}
