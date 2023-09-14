/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class IndyTypeTransformerImpl implements TypeTransformer {
  private final Advice.WithCustomMapping adviceMapping;
  private AgentBuilder.Identified.Extendable agentBuilder;
  private final InstrumentationModule instrumentationModule;

  public IndyTypeTransformerImpl(
      AgentBuilder.Identified.Extendable agentBuilder, InstrumentationModule module) {
    this.agentBuilder = agentBuilder;
    this.instrumentationModule = module;
    this.adviceMapping =
        Advice.withCustomMapping()
            .with(new Advice.AssignReturned.Factory().withSuppressed(Throwable.class))
            .bootstrap(
                IndyBootstrap.getIndyBootstrapMethod(),
                IndyBootstrap.getAdviceBootstrapArguments(instrumentationModule));
  }

  @Override
  public void applyAdviceToMethod(
      ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
    agentBuilder =
        agentBuilder.transform(
            new AgentBuilder.Transformer.ForAdvice(adviceMapping)
                .advice(methodMatcher, adviceClassName)
                .include(instrumentationModule.getClass().getClassLoader())
                .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler()));
  }

  @Override
  public void applyTransformer(AgentBuilder.Transformer transformer) {
    agentBuilder = agentBuilder.transform(transformer);
  }

  public AgentBuilder.Identified.Extendable getAgentBuilder() {
    return agentBuilder;
  }
}
