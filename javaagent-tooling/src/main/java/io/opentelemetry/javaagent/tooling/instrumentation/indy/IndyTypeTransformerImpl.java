/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.Utils;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.TypeConstantAdjustment;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import java.security.ProtectionDomain;

public final class IndyTypeTransformerImpl implements TypeTransformer {
  private AgentBuilder.Identified.Extendable agentBuilder;

  private final ClassLoader instrumentatioModuleClassloader;

  public IndyTypeTransformerImpl(AgentBuilder.Identified.Extendable agentBuilder, ClassLoader instrumentatioModuleClassloader) {
    this.agentBuilder = agentBuilder;
    this.instrumentatioModuleClassloader = instrumentatioModuleClassloader;
  }

  @Override
  public void applyAdviceToMethod(ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
    //boolean validate = false;
    //assert validate = true;
    //if (validate) {
    //  validateAdvice(instrumentation);
    //}
    Advice.WithCustomMapping withCustomMapping = Advice
        .withCustomMapping()
        .with(new Advice.AssignReturned.Factory().withSuppressed(ClassCastException.class));
        //.bind(new SimpleMethodSignatureOffsetMappingFactory())
        //.bind(new AnnotationValueOffsetMappingFactory())
    withCustomMapping = withCustomMapping.bootstrap(IndyBootstrap.getIndyBootstrapMethod());
    StackManipulation exceptionHandler = MethodInvocation.invoke(new MethodDescription.ForLoadedMethod(IndyBootstrap.getExceptionHandlerMethod()));
    agentBuilder = agentBuilder.transform(
        new AgentBuilder.Transformer.ForAdvice(withCustomMapping)
          .advice(methodMatcher, adviceClassName)
          .include(ClassLoader.getSystemClassLoader(), instrumentatioModuleClassloader)
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
