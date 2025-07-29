/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.func.Block;

public class ContinuationStreamInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("ratpack.exec.internal.ContinuationStream");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("ratpack.exec.internal.ContinuationStream"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        namedOneOf("complete", "event").and(takesArgument(0, named("ratpack.func.Block"))),
        ContinuationStreamInstrumentation.class.getName() + "$WrapBlockAdvice");
  }

  @SuppressWarnings("unused")
  public static class WrapBlockAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(@Advice.Argument(value = 0, readOnly = false) Block block) {
      block = BlockWrapper.wrapIfNeeded(block);
    }
  }
}
