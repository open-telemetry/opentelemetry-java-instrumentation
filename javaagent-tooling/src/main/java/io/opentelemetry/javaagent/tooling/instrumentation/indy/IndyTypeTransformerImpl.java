/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.matcher.ElementMatcher;

public final class IndyTypeTransformerImpl implements TypeTransformer {
  private AgentBuilder.Identified.Extendable agentBuilder;

  private final InstrumentationModule instrumentationModule;

  public IndyTypeTransformerImpl(AgentBuilder.Identified.Extendable agentBuilder, InstrumentationModule module) {
    this.agentBuilder = agentBuilder;
    this.instrumentationModule = module;
  }

  @Override
  public void applyAdviceToMethod(ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
    Advice.WithCustomMapping withCustomMapping = Advice
        .withCustomMapping()
        .with(new Advice.AssignReturned.Factory().withSuppressed(ClassCastException.class))
        .bootstrap(IndyBootstrap.getIndyBootstrapMethod(), IndyBootstrap.getAdviceBootstrapArguments(instrumentationModule));
    StackManipulation exceptionHandler = MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(IndyBootstrap.getExceptionHandlerMethod()));
    agentBuilder = agentBuilder.transform(
        new AgentBuilder.Transformer.ForAdvice(withCustomMapping)
          .advice(methodMatcher, adviceClassName)
          .include(ClassLoader.getSystemClassLoader(), instrumentationModule.getClass().getClassLoader())
          .withExceptionHandler(new Advice.ExceptionHandler.Simple(exceptionHandler))
    );
  }

  @Override
  public void applyTransformer(AgentBuilder.Transformer transformer) {
    agentBuilder = agentBuilder.transform(transformer);
  }

  public AgentBuilder.Identified.Extendable getAgentBuilder() {
    return agentBuilder;
  }
}
