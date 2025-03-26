/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import java.io.FileOutputStream;
import java.io.IOException;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.ClassFileLocator.Resolution;
import net.bytebuddy.matcher.ElementMatcher;

public final class IndyTypeTransformerImpl implements TypeTransformer {

  // path (with trailing slash) to dump transformed advice class to
  private static final String DUMP_PATH = null;
  private final Advice.WithCustomMapping adviceMapping;
  private AgentBuilder.Identified.Extendable agentBuilder;
  private final InstrumentationModule instrumentationModule;

  public IndyTypeTransformerImpl(
      AgentBuilder.Identified.Extendable agentBuilder, InstrumentationModule module) {
    this.agentBuilder = agentBuilder;
    this.instrumentationModule = module;
    this.adviceMapping =
        Advice.withCustomMapping()
            .with(
                new ForceDynamicallyTypedAssignReturnedFactory(
                    new Advice.AssignReturned.Factory().withSuppressed(Throwable.class)))
            .bootstrap(
                IndyBootstrap.getIndyBootstrapMethod(),
                IndyBootstrap.getAdviceBootstrapArguments(instrumentationModule),
                TypeDescription.Generic.Visitor.Generalizing.INSTANCE);
  }

  @Override
  public void applyAdviceToMethod(
      ElementMatcher<? super MethodDescription> methodMatcher, String adviceClassName) {
    agentBuilder =
        agentBuilder.transform(
            new AgentBuilder.Transformer.ForAdvice(adviceMapping)
                .advice(methodMatcher, adviceClassName)
                .include(getAdviceLocator(instrumentationModule.getClass().getClassLoader()))
                .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler()));
  }

  private static ClassFileLocator getAdviceLocator(ClassLoader classLoader) {
    ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
    return new AdviceLocator(classFileLocator);
  }

  @Override
  public void applyTransformer(AgentBuilder.Transformer transformer) {
    agentBuilder = agentBuilder.transform(transformer);
  }

  public AgentBuilder.Identified.Extendable getAgentBuilder() {
    return agentBuilder;
  }

  private static class AdviceLocator implements ClassFileLocator {
    private final ClassFileLocator delegate;

    AdviceLocator(ClassFileLocator delegate) {
      this.delegate = delegate;
    }

    @Override
    public Resolution locate(String name) throws IOException {
      Resolution resolution = delegate.locate(name);
      return new AdviceTransformingResolution(name, resolution);
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  private static class AdviceTransformingResolution implements Resolution {
    private final String name;
    private final Resolution delegate;

    AdviceTransformingResolution(String name, Resolution delegate) {
      this.name = name;
      this.delegate = delegate;
    }

    @Override
    public boolean isResolved() {
      return delegate.isResolved();
    }

    @Override
    public byte[] resolve() {
      byte[] bytes = delegate.resolve();
      byte[] result = AdviceTransformer.transform(bytes);
      if (result != null) {
        dump(name, result);
        InstrumentationModuleClassLoader.bytecodeOverride.put(name.replace('/', '.'), result);
      } else {
        result = bytes;
      }
      return result;
    }
  }

  private static void dump(String name, byte[] bytes) {
    if (DUMP_PATH == null) {
      return;
    }
    try (FileOutputStream fos =
        new FileOutputStream(DUMP_PATH + name.replace('/', '.') + ".class")) {
      fos.write(bytes);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
