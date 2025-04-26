/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.ExperimentalInstrumentationModule;
import io.opentelemetry.javaagent.tooling.bytebuddy.ExceptionHandlers;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.Function;
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
  private final boolean transformAdvice;

  public IndyTypeTransformerImpl(
      AgentBuilder.Identified.Extendable agentBuilder, InstrumentationModule module) {
    this.agentBuilder = agentBuilder;
    this.instrumentationModule = module;
    this.transformAdvice =
        !(instrumentationModule instanceof ExperimentalInstrumentationModule)
            || !((ExperimentalInstrumentationModule) instrumentationModule).isIndyReady();
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
      ElementMatcher<? super MethodDescription> methodMatcher,
      Function<Advice.WithCustomMapping, Advice.WithCustomMapping> mappingCustomizer,
      String adviceClassName) {
    // default strategy used by AgentBuilder.Transformer.ForAdvice
    AgentBuilder.PoolStrategy poolStrategy = AgentBuilder.PoolStrategy.Default.FAST;

    agentBuilder =
        agentBuilder.transform(
            new AgentBuilder.Transformer.ForAdvice(mappingCustomizer.apply(adviceMapping))
                .advice(methodMatcher, adviceClassName)
                // advice transformation already performs uninlining
                .with(
                    transformAdvice ? poolStrategy : new AdviceUninliningPoolStrategy(poolStrategy))
                .include(getAdviceLocator(instrumentationModule.getClass().getClassLoader()))
                .withExceptionHandler(ExceptionHandlers.defaultExceptionHandler()));
  }

  private ClassFileLocator getAdviceLocator(ClassLoader classLoader) {
    ClassFileLocator classFileLocator = ClassFileLocator.ForClassLoader.of(classLoader);
    return new AdviceLocator(
        classFileLocator,
        (slashClassName, bytes) -> {
          if (transformAdvice) {
            // rewrite inline advice to a from accepted by indy instrumentation
            return transformAdvice(slashClassName, bytes);
          } else {
            // verify that advice does not call VirtualField.find
            // NOTE: this check is here to help converting the advice for the indy instrumentation,
            // it can be removed once the conversion is completed
            VirtualFieldChecker.check(bytes);
            return bytes;
          }
        });
  }

  private static byte[] transformAdvice(String slashClassName, byte[] bytes) {
    byte[] result = AdviceTransformer.transform(bytes);
    if (result != null) {
      dump(slashClassName, result);
      InstrumentationModuleClassLoader.bytecodeOverride.put(
          slashClassName.replace('/', '.'), result);
    } else {
      result = bytes;
    }
    return result;
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
    private final BiFunction<String, byte[], byte[]> transform;

    AdviceLocator(ClassFileLocator delegate, BiFunction<String, byte[], byte[]> transform) {
      this.delegate = delegate;
      this.transform = transform;
    }

    @Override
    public Resolution locate(String name) throws IOException {
      Resolution resolution = delegate.locate(name);
      return new AdviceTransformingResolution(name, resolution, transform);
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  private static class AdviceTransformingResolution implements Resolution {
    private final String name;
    private final Resolution delegate;
    private final BiFunction<String, byte[], byte[]> transform;

    AdviceTransformingResolution(
        String name, Resolution delegate, BiFunction<String, byte[], byte[]> transform) {
      this.name = name;
      this.delegate = delegate;
      this.transform = transform;
    }

    @Override
    public boolean isResolved() {
      return delegate.isResolved();
    }

    @Override
    public byte[] resolve() {
      byte[] bytes = delegate.resolve();
      return transform.apply(name, bytes);
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
