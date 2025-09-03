/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item;

import static io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.item.ItemSingletons.ITEM_OPERATION_READ;
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
}
