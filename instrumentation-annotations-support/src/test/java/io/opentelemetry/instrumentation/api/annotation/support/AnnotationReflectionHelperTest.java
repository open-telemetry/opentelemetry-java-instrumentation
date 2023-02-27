/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AnnotationReflectionHelperTest {

  @Test
  void returnTheAnnotationTypeByName() {
    Class<? extends Annotation> clazz =
        AnnotationReflectionHelper.forNameOrNull(
            AnnotationReflectionHelperTest.class.getClassLoader(),
            "io.opentelemetry.instrumentation.api.annotation.support.CustomAnnotation");
    assertThat(clazz).isEqualTo(CustomAnnotation.class);
  }

  @Test
  void returnsNullForAnAnnotationNotFound() {
    Class<? extends Annotation> clazz =
        AnnotationReflectionHelper.forNameOrNull(
            AnnotationReflectionHelperTest.class.getClassLoader(),
            "io.opentelemetry.instrumentation.api.annotation.support.NonExistentAnnotation");
    assertThat(clazz).isNull();
  }

  @Test
  void returnsNullForClassThatIsNotAnnotation() {
    Class<? extends Annotation> clazz =
        AnnotationReflectionHelper.forNameOrNull(
            AnnotationReflectionHelperTest.class.getClassLoader(), "java.util.List");
    assertThat(clazz).isNull();
  }

  @Test
  void returnsBoundFunctionalInterfaceToAnnotationElement() throws Throwable {
    Function<Annotation, String> function =
        AnnotationReflectionHelper.bindAnnotationElementMethod(
            MethodHandles.lookup(), CustomAnnotation.class, "value", String.class);
    CustomAnnotation annotation = Annotated.class.getDeclaredAnnotation(CustomAnnotation.class);

    String result = function.apply(annotation);

    assertThat(result).isEqualTo("Value");
  }

  @CustomAnnotation(value = "Value")
  static class Annotated {}
}
