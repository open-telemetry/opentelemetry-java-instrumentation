/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.asm.Advice.AssignReturned.ToFields.ToField;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.junit.jupiter.api.Test;

public class ForceDynamicallyTypedAssignReturnedFactoryTest {

  @AssignReturned.ToFields(@ToField(value = "foo", index = 42))
  @AssignReturned.ToArguments(@ToArgument(value = 3, index = 7))
  @AssignReturned.ToReturned(index = 4)
  @AssignReturned.ToThrown(index = 5)
  @AssignReturned.ToThis(index = 6)
  @AssignReturned.ToAllArguments(index = 7)
  @Advice.OnMethodEnter
  static void testMethod() {}

  @Test
  public void checkTypingMadeDynamic() {
    MethodDescription.InDefinedShape original =
        TypeDescription.ForLoadedType.of(ForceDynamicallyTypedAssignReturnedFactoryTest.class)
            .getDeclaredMethods()
            .stream()
            .filter(method -> method.getName().equals("testMethod"))
            .findFirst()
            .get();

    ClassLoader cl = ForceDynamicallyTypedAssignReturnedFactoryTest.class.getClassLoader();

    List<? extends AnnotationDescription> modifiedAnnotations =
        ForceDynamicallyTypedAssignReturnedFactory.forceDynamicTyping(
            original.getDeclaredAnnotations());
    assertThat(modifiedAnnotations)
        .hasSize(7)
        .anySatisfy(
            toFields -> {
              assertThat(toFields.getAnnotationType().getName())
                  .isEqualTo(AssignReturned.ToFields.class.getName());
              assertThat((AnnotationDescription[]) toFields.getValue("value").resolve())
                  .hasSize(1)
                  .anySatisfy(
                      toField -> {
                        assertThat(toField.getValue("value").resolve()).isEqualTo("foo");
                        assertThat(toField.getValue("index").resolve()).isEqualTo(42);
                        assertThat(toField.getValue("typing").load(cl).resolve())
                            .isEqualTo(Assigner.Typing.DYNAMIC);
                      });
            })
        .anySatisfy(
            toArguments -> {
              assertThat(toArguments.getAnnotationType().getName())
                  .isEqualTo(AssignReturned.ToArguments.class.getName());
              assertThat((AnnotationDescription[]) toArguments.getValue("value").resolve())
                  .hasSize(1)
                  .anySatisfy(
                      toArgument -> {
                        assertThat(toArgument.getValue("value").resolve()).isEqualTo(3);
                        assertThat(toArgument.getValue("index").resolve()).isEqualTo(7);
                        assertThat(toArgument.getValue("typing").load(cl).resolve())
                            .isEqualTo(Assigner.Typing.DYNAMIC);
                      });
            })
        .anySatisfy(
            toReturned -> {
              assertThat(toReturned.getAnnotationType().getName())
                  .isEqualTo(AssignReturned.ToReturned.class.getName());
              assertThat(toReturned.getValue("index").resolve()).isEqualTo(4);
              assertThat(toReturned.getValue("typing").load(cl).resolve())
                  .isEqualTo(Assigner.Typing.DYNAMIC);
            })
        .anySatisfy(
            toThrown -> {
              assertThat(toThrown.getAnnotationType().getName())
                  .isEqualTo(AssignReturned.ToThrown.class.getName());
              assertThat(toThrown.getValue("index").resolve()).isEqualTo(5);
              assertThat(toThrown.getValue("typing").load(cl).resolve())
                  .isEqualTo(Assigner.Typing.DYNAMIC);
            })
        .anySatisfy(
            toThis -> {
              assertThat(toThis.getAnnotationType().getName())
                  .isEqualTo(AssignReturned.ToThis.class.getName());
              assertThat(toThis.getValue("index").resolve()).isEqualTo(6);
              assertThat(toThis.getValue("typing").load(cl).resolve())
                  .isEqualTo(Assigner.Typing.DYNAMIC);
            })
        .anySatisfy(
            toAllArguments -> {
              assertThat(toAllArguments.getAnnotationType().getName())
                  .isEqualTo(AssignReturned.ToAllArguments.class.getName());
              assertThat(toAllArguments.getValue("index").resolve()).isEqualTo(7);
              assertThat(toAllArguments.getValue("typing").load(cl).resolve())
                  .isEqualTo(Assigner.Typing.DYNAMIC);
            })
        .anySatisfy(
            onMethodEnter -> {
              assertThat(onMethodEnter.getAnnotationType().getName())
                  .isEqualTo(Advice.OnMethodEnter.class.getName());
            });
  }
}
