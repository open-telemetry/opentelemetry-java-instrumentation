/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.classloader;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

class AdviceUtil {
  private static final Advice.WithCustomMapping adviceMapping =
      Advice.withCustomMapping()
          .with(new Advice.AssignReturned.Factory().withSuppressed(Throwable.class));

  static void applyInlineAdvice(
      TypeTransformer transformer, ElementMatcher<MethodDescription> matcher, String adviceClass) {
    transformer.applyTransformer(
        new AgentBuilder.Transformer.ForAdvice(adviceMapping)
            .include(Utils.getBootstrapProxy(), Utils.getAgentClassLoader())
            .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
            .advice(matcher, adviceClass));
  }

  private AdviceUtil() {}
}
