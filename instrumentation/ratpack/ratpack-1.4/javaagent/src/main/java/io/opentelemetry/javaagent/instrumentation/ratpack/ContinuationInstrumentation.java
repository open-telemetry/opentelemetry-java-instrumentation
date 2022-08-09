/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import ratpack.func.Block;

public class ContinuationInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("ratpack.exec.internal.Continuation");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return nameStartsWith("ratpack.exec.")
        .and(implementsInterface(named("ratpack.exec.internal.Continuation")));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("resume").and(takesArgument(0, named("ratpack.func.Block"))),
        ContinuationInstrumentation.class.getName() + "$ResumeAdvice");
  }

  @SuppressWarnings("unused")
  public static class ResumeAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void wrap(@Advice.Argument(value = 0, readOnly = false) Block block) {
      block = BlockWrapper.wrapIfNeeded(block);
    }
  }
}
