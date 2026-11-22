/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.extension.matcher.internal.MethodAnnotationPrefilter;
import io.opentelemetry.test.AnnotatedTestClass;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class AgentCachingPoolStrategyTest {

  private static final String SUBJECT = "example.AnnotationPrefilterTarget";

  @Test
  void testSkipResourceLookupForAnnotations() {
    ClassLoader classLoader =
        new ClassLoader(AgentCachingPoolStrategyTest.class.getClassLoader()) {

          private void checkResource(String name) {
            if (name.contains("TestAnnotation")) {
              throw new IllegalStateException("Unexpected resource lookup for " + name);
            }
          }

          @Override
          public URL getResource(String name) {
            checkResource(name);
            return super.getResource(name);
          }

          @Override
          public InputStream getResourceAsStream(String name) {
            checkResource(name);
            return super.getResourceAsStream(name);
          }

          @Override
          public Enumeration<URL> getResources(String name) throws IOException {
            checkResource(name);
            return super.getResources(name);
          }
        };

    ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(classLoader);
    TypePool pool = AgentTooling.poolStrategy().typePool(locator, classLoader);
    TypePool.Resolution resolution = pool.describe(AnnotatedTestClass.class.getName());
    TypeDescription typeDescription = resolution.resolve();

    assertThat(isAnnotatedWith(AnnotatedTestClass.TestAnnotation.class).matches(typeDescription))
        .isTrue();
    assertThat(
            declaresMethod(isAnnotatedWith(AnnotatedTestClass.TestAnnotation.class))
                .matches(typeDescription))
        .isTrue();
  }

  @Test
  void reusesProvenAbsenceOnlyWithinTheSameLoader() {
    MethodAnnotationPrefilter prefilter =
        new MethodAnnotationPrefilter(AnnotatedTestClass.TestAnnotation.class.getName());
    ClassLoader loader = new ClassLoader() {};
    TypeDescription unannotated = describe(loader, classWithMethod(false));
    assertThat(prefilter.matches(unannotated)).isFalse();
    assertThat(prefilter.matches(describe(loader, ClassFileLocator.NoOp.INSTANCE))).isFalse();

    TypeDescription annotated = describe(new ClassLoader() {}, classWithMethod(true));
    assertThat(
            prefilter
                .and(declaresMethod(isAnnotatedWith(AnnotatedTestClass.TestAnnotation.class)))
                .matches(annotated))
        .isTrue();
  }

  @Test
  void doesNotTreatMissingBytecodeAsAnUnannotatedType() {
    MethodAnnotationPrefilter prefilter =
        new MethodAnnotationPrefilter(AnnotatedTestClass.TestAnnotation.class.getName());
    assertThat(prefilter.matches(describe(new ClassLoader() {}, ClassFileLocator.NoOp.INSTANCE)))
        .isTrue();
  }

  @Test
  void checksTheCurrentTransformationBytesInsteadOfCachedAbsence() {
    MethodAnnotationPrefilter prefilter =
        new MethodAnnotationPrefilter(AnnotatedTestClass.TestAnnotation.class.getName());
    ClassLoader loader = new ClassLoader() {};
    assertThat(prefilter.matches(describe(loader, classWithMethod(false)))).isFalse();

    AgentBuilder.Listener listener = AgentTooling.transformListener();
    listener.onDiscovery(SUBJECT, loader, JavaModule.UNSUPPORTED, false);
    try {
      assertThat(
              prefilter
                  .and(declaresMethod(isAnnotatedWith(AnnotatedTestClass.TestAnnotation.class)))
                  .matches(describe(loader, classWithMethod(true))))
          .isTrue();
    } finally {
      listener.onComplete(SUBJECT, loader, JavaModule.UNSUPPORTED, false);
    }
  }

  private static TypeDescription describe(ClassLoader loader, ClassFileLocator locator) {
    return AgentTooling.poolStrategy().typePool(locator, loader).describe(SUBJECT).resolve();
  }

  private static ClassFileLocator classWithMethod(boolean annotated) {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        SUBJECT.replace('.', '/'),
        null,
        "java/lang/Object",
        null);
    MethodVisitor method =
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "example", "()V", null, null);
    if (annotated) {
      method
          .visitAnnotation(Type.getDescriptor(AnnotatedTestClass.TestAnnotation.class), true)
          .visitEnd();
    }
    method.visitEnd();
    writer.visitEnd();
    return ClassFileLocator.Simple.of(SUBJECT, writer.toByteArray());
  }
}
