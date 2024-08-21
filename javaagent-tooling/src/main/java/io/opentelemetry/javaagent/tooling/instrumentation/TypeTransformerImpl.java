/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.ForceDynamicallyTypedAssignReturnedFactory;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class TypeTransformerImpl implements TypeTransformer {
  private AgentBuilder.Identified.Extendable agentBuilder;
  private final Advice.WithCustomMapping adviceMapping;

  TypeTransformerImpl(AgentBuilder.Identified.Extendable agentBuilder) {
    this.agentBuilder = agentBuilder;
    adviceMapping =
        Advice.withCustomMapping()
            .with(
                new ForceDynamicallyTypedAssignReturnedFactory(
                    new Advice.AssignReturned.Factory().withSuppressed(Throwable.class)));
  }

  @Override
  public void applyAdviceToMethod(
      ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
    agentBuilder =
        agentBuilder.transform(
            new AgentBuilder.Transformer.ForAdvice(adviceMapping)
                .include(
                    Utils.getBootstrapProxy(),
                    Utils.getAgentClassLoader(),
                    Utils.getExtensionsClassLoader())
                .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler())
                .advice(methodMatcher, adviceClassName));
  }

  @Override
  public void applyTransformer(AgentBuilder.Transformer transformer) {
    agentBuilder = agentBuilder.transform(transformer);
  }

  AgentBuilder.Identified.Extendable getAgentBuilder() {
    return agentBuilder;
  }
}
