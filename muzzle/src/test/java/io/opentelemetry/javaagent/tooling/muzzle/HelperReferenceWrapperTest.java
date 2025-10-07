/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.atIndex;

import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    // helperWrapper assertions
    assertThat(helperWrapper.isAbstract()).isFalse();

    assertThat(helperWrapper.getMethods())
        .hasSize(1)
        .satisfies(method -> {
          assertThat(method.isAbstract()).isFalse();
          assertThat(method.getName()).isEqualTo("bar");
          assertThat(method.getDescriptor()).isEqualTo("()V");
        }, atIndex(0));

    List<HelperReferenceWrapper.Field> helperFields =
        helperWrapper.getFields().collect(Collectors.toList());
    assertThat(helperFields)
        .hasSize(1)
        .satisfies(field -> {
          assertThat(field.getName()).isEqualTo("declaredField");
          assertThat(field.getDescriptor()).isEqualTo("Ljava/lang/Object;");
        }, atIndex(0));

    assertThat(helperWrapper.hasSuperTypes()).isTrue();
    List<HelperReferenceWrapper> superTypes =
        helperWrapper.getSuperTypes().collect(Collectors.toList());
    assertThat(superTypes).hasSize(2);

    // baseHelper assertions
    HelperReferenceWrapper baseHelper = superTypes.get(0);
    assertThat(baseHelper.isAbstract()).isTrue();
    List<HelperReferenceWrapper.Method> baseHelperMethods =
        baseHelper.getMethods().collect(Collectors.toList());
    assertThat(baseHelperMethods)
        .hasSize(2)
        .satisfies(method -> {
          assertThat(method.isAbstract()).isFalse();
          assertThat(method.getName()).isEqualTo("foo");
          assertThat(method.getDescriptor()).isEqualTo("()V");
        }, atIndex(0))
        .satisfies(method -> {
          assertThat(method.isAbstract()).isTrue();
          assertThat(method.getName()).isEqualTo("abstract");
          assertThat(method.getDescriptor()).isEqualTo("()I");
        }, atIndex(1));

    assertThat(baseHelper.hasSuperTypes()).isTrue();
    List<HelperReferenceWrapper> baseSuperTypes =
        baseHelper.getSuperTypes().collect(Collectors.toList());
    assertThat(baseSuperTypes).hasSize(1);
    HelperReferenceWrapper abstractClasspathType = baseSuperTypes.get(0);
    assertThat(abstractClasspathType.isAbstract()).isTrue();
    assertThat(abstractClasspathType.getMethods().collect(Collectors.toList())).isEmpty();
    List<HelperReferenceWrapper.Field> abstractFields =
        abstractClasspathType.getFields().collect(Collectors.toList());
    assertThat(abstractFields)
        .hasSize(1)
        .satisfies(field -> {
          assertThat(field.getName()).isEqualTo("field");
          assertThat(field.getDescriptor()).isEqualTo("Ljava/lang/Object;");
        }, atIndex(0));

    assertThat(abstractClasspathType.hasSuperTypes()).isTrue();
    List<HelperReferenceWrapper> abstractSuperTypes =
        abstractClasspathType.getSuperTypes().collect(Collectors.toList());
    assertThat(abstractSuperTypes).hasSize(2);
    HelperReferenceWrapper objectType = abstractSuperTypes.get(0);
    assertThat(objectType.hasSuperTypes()).isFalse();
    HelperReferenceWrapper interface1 = abstractSuperTypes.get(1);
    assertThat(interface1.isAbstract()).isTrue();
    List<HelperReferenceWrapper.Method> interface1Methods =
        interface1.getMethods().collect(Collectors.toList());
    assertThat(interface1Methods)
        .hasSize(1)
        .satisfies(method -> {
          assertThat(method.isAbstract()).isTrue();
          assertThat(method.getName()).isEqualTo("foo");
          assertThat(method.getDescriptor()).isEqualTo("()V");
        }, atIndex(0));
    assertThat(interface1.hasSuperTypes()).isFalse();
    assertThat(interface1.getSuperTypes().collect(Collectors.toList())).isEmpty();

    // interface2 assertions
    HelperReferenceWrapper interface2 = superTypes.get(1);
    assertThat(interface2.isAbstract()).isTrue();
    List<HelperReferenceWrapper.Method> interface2Methods =
        interface2.getMethods().collect(Collectors.toList());
    assertThat(interface2Methods)
        .hasSize(1)
        .satisfies(method -> {
          assertThat(method.isAbstract()).isTrue();
          assertThat(method.getName()).isEqualTo("bar");
          assertThat(method.getDescriptor()).isEqualTo("()V");
        }, atIndex(0));
    assertThat(interface2.hasSuperTypes()).isFalse();
    assertThat(interface2.getSuperTypes().collect(Collectors.toList())).isEmpty();
  }
}
