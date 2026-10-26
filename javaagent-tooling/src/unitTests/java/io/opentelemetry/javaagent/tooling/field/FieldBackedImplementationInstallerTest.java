/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static java.util.concurrent.TimeUnit.SECONDS;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappingsBuilderImpl;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.AsmVisitorWrapper;
import org.junit.jupiter.api.Test;

class FieldBackedImplementationInstallerTest {

  @Test
  void generatesAndReusesHelpersOnlyWhenTransforming() throws IllegalClassFormatException {
    FieldBackedImplementationInstaller installer = installer();
    AgentBuilder.Identified.Extendable builder = installer.rewriteVirtualFieldsCalls(builder());
    installer.injectHelperClasses(builder);
    assertThat(installer.generatedClasses.get()).isNull();

    ClassFileTransformer transformer = builder.makeRaw();
    transform(transformer);
    Object generated = installer.generatedClasses.get();
    assertThat(generated).isNotNull();

    transform(transformer);
    assertThat(installer.generatedClasses.get()).isSameAs(generated);
  }

  @Test
  void concurrentTransformationsUseTheSameHelpers()
      throws ExecutionException, InterruptedException, TimeoutException {
    FieldBackedImplementationInstaller installer = installer();

    ExecutorService executor = Executors.newFixedThreadPool(4);
    CyclicBarrier barrier = new CyclicBarrier(4);
    try {
      List<Future<FieldBackedImplementationInstaller.GeneratedClasses>> results = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        results.add(
            executor.submit(
                () -> {
                  AtomicReference<FieldBackedImplementationInstaller.GeneratedClasses> usedHelpers =
                      new AtomicReference<>();
                  ClassFileTransformer transformer =
                      builder()
                          .transform(
                              installer.getTransformerForAsmVisitor(
                                  classes -> {
                                    usedHelpers.set(classes);
                                    return new AsmVisitorWrapper.ForDeclaredMethods();
                                  }))
                          .makeRaw();
                  barrier.await(30, SECONDS);
                  transform(transformer);
                  return usedHelpers.get();
                }));
      }
      List<FieldBackedImplementationInstaller.GeneratedClasses> usedHelpers = new ArrayList<>();
      for (Future<FieldBackedImplementationInstaller.GeneratedClasses> result : results) {
        usedHelpers.add(result.get(30, SECONDS));
      }
      FieldBackedImplementationInstaller.GeneratedClasses published =
          installer.generatedClasses.get();
      assertThat(published).isNotNull();
      for (FieldBackedImplementationInstaller.GeneratedClasses used : usedHelpers) {
        assertThat(used).isSameAs(published);
      }
    } finally {
      executor.shutdownNow();
      assertThat(executor.awaitTermination(30, SECONDS)).isTrue();
    }
  }

  private static FieldBackedImplementationInstaller installer() {
    VirtualFieldMappingsBuilderImpl mappings = new VirtualFieldMappingsBuilderImpl();
    mappings.register("java.lang.Runnable", "java.lang.String");
    return new FieldBackedImplementationInstaller(
        FieldBackedImplementationInstallerTest.class, mappings.build());
  }

  private static AgentBuilder.Identified.Extendable builder() {
    return new AgentBuilder.Default()
        .with(AgentBuilder.Listener.StreamWriting.toSystemError())
        .disableClassFormatChanges()
        .type(named("startup.TestTarget"))
        .transform(
            (dynamicBuilder, typeDescription, classLoader, javaModule, protectionDomain) ->
                dynamicBuilder);
  }

  private static void transform(ClassFileTransformer transformer)
      throws IllegalClassFormatException {
    byte[] bytes =
        new ByteBuddy().subclass(Object.class).name("startup.TestTarget").make().getBytes();
    assertThat(
            transformer.transform(
                FieldBackedImplementationInstallerTest.class.getClassLoader(),
                "startup/TestTarget",
                null,
                null,
                bytes))
        .isNotNull();
  }
}
