/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import java.util.HashMap;
import java.util.Map;
import muzzle.HelperReferenceWrapperTestClasses;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

class HelperReferenceWrapperTest {

  private static ClassRef baseHelperClass;
  private static ClassRef helperClass;

  @BeforeAll
  static void setup() {
    baseHelperClass =
        ClassRef.builder(HelperReferenceWrapperTest.class.getName() + "$BaseHelper")
            .setSuperClassName(
                HelperReferenceWrapperTestClasses.AbstractClasspathType.class.getName())
            .addFlag(ManifestationFlag.ABSTRACT)
            .addMethod(new Source[0], new Flag[0], "foo", Type.VOID_TYPE)
            .addMethod(
                new Source[0], new Flag[] {ManifestationFlag.ABSTRACT}, "abstract", Type.INT_TYPE)
            .build();

    helperClass =
        ClassRef.builder(HelperReferenceWrapperTest.class.getName() + "$Helper")
            .setSuperClassName(baseHelperClass.getClassName())
            .addInterfaceName(HelperReferenceWrapperTestClasses.Interface2.class.getName())
            .addMethod(new Source[0], new Flag[0], "bar", Type.VOID_TYPE)
            .addField(
                new Source[0], new Flag[0], "field", Type.getType("Ljava/lang/Object;"), false)
            .addField(
                new Source[0],
                new Flag[0],
                "declaredField",
                Type.getType("Ljava/lang/Object;"),
                true)
            .addField(
                new Source[0],
                new Flag[] {Flag.VisibilityFlag.PRIVATE},
                "privateFieldsAreSkipped",
                Type.getType("Ljava/lang/Object;"),
                true)
            .build();
  }

  @Test
  void shouldWrapHelperTypes() {
    TypePool typePool = TypePool.Default.of(HelperReferenceWrapperTest.class.getClassLoader());
    Map<String, ClassRef> references = new HashMap<>();
    references.put(helperClass.getClassName(), helperClass);
    references.put(baseHelperClass.getClassName(), baseHelperClass);

    HelperClassPredicate helperClassPredicate = new HelperClassPredicate(cls -> false);
    HelperReferenceWrapper helperWrapper =
        new HelperReferenceWrapper.Factory(typePool, references, helperClassPredicate)
            .create(helperClass);

    assertThat(helperWrapper.isAbstract()).isFalse();

    assertThat(helperWrapper.getMethods())
        .satisfiesExactly(
            method -> {
              assertThat(method.isAbstract()).isFalse();
              assertThat(method.getName()).isEqualTo("bar");
              assertThat(method.getDescriptor()).isEqualTo("()V");
            });

    assertThat(helperWrapper.getFields())
        .satisfiesExactly(
            field -> {
              assertThat(field.getName()).isEqualTo("declaredField");
              assertThat(field.getDescriptor()).isEqualTo("Ljava/lang/Object;");
            });

    assertThat(helperWrapper.hasSuperTypes()).isTrue();
    assertThat(helperWrapper.getSuperTypes())
        .satisfiesExactly(
            baseHelper -> {
              assertThat(baseHelper.isAbstract()).isTrue();
              assertThat(baseHelper.getMethods())
                  .satisfiesExactly(
                      method -> {
                        assertThat(method.isAbstract()).isFalse();
                        assertThat(method.getName()).isEqualTo("foo");
                        assertThat(method.getDescriptor()).isEqualTo("()V");
                      },
                      method -> {
                        assertThat(method.isAbstract()).isTrue();
                        assertThat(method.getName()).isEqualTo("abstract");
                        assertThat(method.getDescriptor()).isEqualTo("()I");
                      });

              assertThat(baseHelper.hasSuperTypes()).isTrue();
              assertThat(baseHelper.getSuperTypes())
                  .satisfiesExactly(
                      helperReferenceWrapper -> {
                        assertThat(helperReferenceWrapper.isAbstract()).isTrue();
                        assertThat(helperReferenceWrapper.getMethods()).isEmpty();

                        assertThat(helperReferenceWrapper.getFields())
                            .satisfiesExactly(
                                field -> {
                                  assertThat(field.getName()).isEqualTo("field");
                                  assertThat(field.getDescriptor()).isEqualTo("Ljava/lang/Object;");
                                });

                        assertThat(helperReferenceWrapper.hasSuperTypes()).isTrue();
                        assertThat(helperReferenceWrapper.getSuperTypes())
                            .satisfiesExactly(
                                wrapper -> assertThat(wrapper.hasSuperTypes()).isFalse(),
                                wrapper -> {
                                  assertThat(wrapper.isAbstract()).isTrue();
                                  assertThat(wrapper.getMethods())
                                      .satisfiesExactly(
                                          method -> {
                                            assertThat(method.isAbstract()).isTrue();
                                            assertThat(method.getName()).isEqualTo("foo");
                                            assertThat(method.getDescriptor()).isEqualTo("()V");
                                          });
                                  assertThat(wrapper.hasSuperTypes()).isFalse();
                                  assertThat(wrapper.getSuperTypes()).isEmpty();
                                });
                      });
            },
            wrapper -> {
              assertThat(wrapper.isAbstract()).isTrue();
              assertThat(wrapper.getMethods())
                  .satisfiesExactly(
                      method -> {
                        assertThat(method.isAbstract()).isTrue();
                        assertThat(method.getName()).isEqualTo("bar");
                        assertThat(method.getDescriptor()).isEqualTo("()V");
                      });
              assertThat(wrapper.hasSuperTypes()).isFalse();
              assertThat(wrapper.getSuperTypes()).isEmpty();
            });
  }
}
