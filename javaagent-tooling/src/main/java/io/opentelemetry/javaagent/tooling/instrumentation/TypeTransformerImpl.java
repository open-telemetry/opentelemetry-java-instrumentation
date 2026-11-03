/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.AdviceBootstrap;
import io.opentelemetry.javaagent.tooling.instrumentation.indy.ForceDynamicallyTypedAssignReturnedFactory;
import java.util.function.Function;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public final class TypeTransformerImpl implements TypeTransformer {

  private final Advice.WithCustomMapping adviceMapping;
  private AgentBuilder.Identified.Extendable agentBuilder;
  private final InstrumentationModule instrumentationModule;

  public TypeTransformerImpl(
      AgentBuilder.Identified.Extendable agentBuilder, InstrumentationModule module) {
    this.agentBuilder = agentBuilder;
    this.instrumentationModule = module;
    this.adviceMapping =
        Advice.withCustomMapping()
            .with(
                new ForceDynamicallyTypedAssignReturnedFactory(
                    new Advice.AssignReturned.Factory().withSuppressed(Throwable.class)))
            .bootstrap(
                AdviceBootstrap.getBootstrapMethod(),
                AdviceBootstrap.createBootstrapArgumentResolverFactory(instrumentationModule),
                TypeDescription.Generic.Visitor.Generalizing.INSTANCE);
  }

  @Override
  public void applyAdviceToMethod(
      ElementMatcher<? super MethodDescription> methodMatcher,
      Function<Advice.WithCustomMapping, Advice.WithCustomMapping> mappingCustomizer,
      String adviceClassName) {
    agentBuilder =
        agentBuilder.transform(
            new AgentBuilder.Transformer.ForAdvice(mappingCustomizer.apply(adviceMapping))
                .advice(methodMatcher, adviceClassName)
                // default strategy used by AgentBuilder.Transformer.ForAdvice
                .with(AgentBuilder.PoolStrategy.Default.FAST)
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
