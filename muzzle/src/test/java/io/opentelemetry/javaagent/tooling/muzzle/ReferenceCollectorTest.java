/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PACKAGE_OR_HIGHER;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PROTECTED_OR_HIGHER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import external.instrumentation.ExternalHelper;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.OtherTestHelperClasses;
import io.opentelemetry.instrumentation.TestHelperClasses;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldTestClasses.State;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.FieldRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.OwnershipFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag.VisibilityFlag;
import io.opentelemetry.javaagent.tooling.muzzle.references.MethodRef;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import muzzle.TestClasses;
import muzzle.TestClasses.HelperAdvice;
import muzzle.TestClasses.LdcAdvice;
import muzzle.TestClasses.MethodBodyAdvice;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Type;

class ReferenceCollectorTest {

  @Test
  public void methodBodyCreatesReferences() {
    ReferenceCollector collector = new ReferenceCollector((String s) -> false);

    collector.collectReferencesFromAdvice(MethodBodyAdvice.class.getName());
    collector.prune();
    Map<String, ClassRef> references = collector.getReferences();

    assertThat(references)
        .containsOnlyKeys(
            MethodBodyAdvice.A.class.getName(),
            MethodBodyAdvice.B.class.getName(),
            MethodBodyAdvice.SomeInterface.class.getName(),
            MethodBodyAdvice.SomeImplementation.class.getName());

    ClassRef refB = references.get(MethodBodyAdvice.B.class.getName());
    ClassRef refA = references.get(MethodBodyAdvice.A.class.getName());

    // interface flags
    assertThat(refB.getFlags()).contains(ManifestationFlag.NON_INTERFACE);
    assertThat(references.get(MethodBodyAdvice.SomeInterface.class.getName()).getFlags())
        .contains(ManifestationFlag.INTERFACE);

    // class access flags
    assertThat(refA.getFlags()).contains(PACKAGE_OR_HIGHER);
    assertThat(refB.getFlags()).contains(PACKAGE_OR_HIGHER);

    // method refs
    assertMethod(
        refB,
        "method",
        "(Ljava/lang/String;)Ljava/lang/String;",
        PROTECTED_OR_HIGHER,
        OwnershipFlag.NON_STATIC);
    assertMethod(
        refB, "methodWithPrimitives", "(Z)V", PROTECTED_OR_HIGHER, OwnershipFlag.NON_STATIC);
    assertMethod(refB, "staticMethod", "()V", PROTECTED_OR_HIGHER, OwnershipFlag.STATIC);
    assertMethod(
        refB,
        "methodWithArrays",
        "([Ljava/lang/String;)[Ljava/lang/Object;",
        PROTECTED_OR_HIGHER,
        OwnershipFlag.NON_STATIC);

    // field refs
    assertThat(refB.getFields()).isEmpty();
    assertThat(refA.getFields()).hasSize(2);
    assertField(refA, "publicB", PACKAGE_OR_HIGHER, OwnershipFlag.NON_STATIC);
    assertField(refA, "staticB", PACKAGE_OR_HIGHER, OwnershipFlag.STATIC);
  }

  @Test
  public void protectedRefTest() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(MethodBodyAdvice.B2.class.getName());
    collector.prune();
    Map<String, ClassRef> references = collector.getReferences();

    assertMethod(
        references.get(MethodBodyAdvice.B.class.getName()),
        "protectedMethod",
        "()V",
        PROTECTED_OR_HIGHER,
        OwnershipFlag.NON_STATIC);
  }

  @Test
  public void ldcCreatesReferences() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(LdcAdvice.class.getName());
    collector.prune();
    Map<String, ClassRef> references = collector.getReferences();

    assertThat(references).containsKey(MethodBodyAdvice.A.class.getName());
  }

  @Test
  public void instanceofCreatesReferences() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(TestClasses.InstanceofAdvice.class.getName());
    collector.prune();
    Map<String, ClassRef> references = collector.getReferences();

    assertThat(references).containsKey(MethodBodyAdvice.A.class.getName());
  }

  @Test
  public void invokedynamicCreatesReferences() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(TestClasses.InvokeDynamicAdvice.class.getName());
    collector.prune();
    Map<String, ClassRef> references = collector.getReferences();

    assertThat(references).containsKey("muzzle.TestClasses$MethodBodyAdvice$SomeImplementation");
    assertMethod(
        references.get("muzzle.TestClasses$MethodBodyAdvice$SomeImplementation"),
        "someMethod",
        "()V",
        PROTECTED_OR_HIGHER,
        OwnershipFlag.NON_STATIC);
    assertThat(references).containsKey("muzzle.TestClasses$MethodBodyAdvice$B");
    assertMethod(
        references.get("muzzle.TestClasses$MethodBodyAdvice$B"),
        "staticMethod",
        "()V",
        PROTECTED_OR_HIGHER,
        OwnershipFlag.STATIC);
    assertThat(references).containsKey("muzzle.TestClasses$MethodBodyAdvice$A");
    assertMethod(
        references.get("muzzle.TestClasses$MethodBodyAdvice$A"),
        "<init>",
        "()V",
        PROTECTED_OR_HIGHER,
        OwnershipFlag.NON_STATIC);
  }

  @Test
  public void shouldCreateReferencesForHelperClasses() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(HelperAdvice.class.getName());
    Map<String, ClassRef> references = collector.getReferences();

    assertThat(references)
        .containsOnlyKeys(
            TestHelperClasses.Helper.class.getName(),
            TestHelperClasses.HelperSuperClass.class.getName(),
            TestHelperClasses.HelperInterface.class.getName());

    ClassRef helperSuperClass = references.get(TestHelperClasses.HelperSuperClass.class.getName());
    assertThat(helperSuperClass.getFlags()).contains(ManifestationFlag.ABSTRACT);
    assertHelperSuperClassMethod(helperSuperClass, true);
    assertMethod(
        helperSuperClass,
        "finalMethod",
        "()Ljava/lang/String;",
        VisibilityFlag.PUBLIC,
        OwnershipFlag.NON_STATIC,
        ManifestationFlag.FINAL);

    ClassRef helperInterface = references.get(TestHelperClasses.HelperInterface.class.getName());
    assertThat(helperInterface.getFlags()).contains(ManifestationFlag.ABSTRACT);
    assertHelperInterfaceMethod(helperInterface, true);

    ClassRef helperClass = references.get(TestHelperClasses.Helper.class.getName());
    assertThat(helperClass.getFlags()).contains(ManifestationFlag.NON_FINAL);
    assertHelperSuperClassMethod(helperClass, false);
    assertHelperInterfaceMethod(helperClass, false);
  }

  @Test
  public void shouldCollectFieldDeclarationReferences() {
    ReferenceCollector collector =
        new ReferenceCollector(s -> s.equals(DeclaredFieldTestClass.Helper.class.getName()));
    collector.collectReferencesFromAdvice(DeclaredFieldTestClass.Advice.class.getName());
    collector.prune();
    Map<String, ClassRef> references = collector.getReferences();

    ClassRef helperClass = references.get(DeclaredFieldTestClass.Helper.class.getName());
    FieldRef superField = findField(helperClass, "superField");
    assertThat(superField).isNotNull();
    assertThat(superField.isDeclared()).isFalse();

    FieldRef field = findField(helperClass, "helperField");
    assertThat(field).isNotNull();
    assertThat(field.isDeclared()).isTrue();

    ClassRef libraryBaseClass =
        references.get(DeclaredFieldTestClass.LibraryBaseClass.class.getName());
    assertThat(libraryBaseClass.getFields()).isEmpty();
  }

  @Test
  public void shouldFindAllHelperClasses() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(HelperAdvice.class.getName());
    collector.prune();
    List<String> helperClasses = collector.getSortedHelperClasses();

    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                TestHelperClasses.HelperInterface.class.getName(),
                TestHelperClasses.Helper.class.getName()));
    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                TestHelperClasses.HelperSuperClass.class.getName(),
                TestHelperClasses.Helper.class.getName()));
  }

  @Test
  public void shouldCorrectlyFindHelperClassesFromMultipleAdviceClasses() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(HelperAdvice.class.getName());
    collector.collectReferencesFromAdvice(TestClasses.HelperOtherAdvice.class.getName());
    collector.prune();
    List<String> helperClasses = collector.getSortedHelperClasses();

    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                TestHelperClasses.HelperInterface.class.getName(),
                TestHelperClasses.Helper.class.getName()));
    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                TestHelperClasses.HelperSuperClass.class.getName(),
                TestHelperClasses.Helper.class.getName()));
    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                OtherTestHelperClasses.TestEnum.class.getName(),
                OtherTestHelperClasses.TestEnum.class.getName() + "$1"));

    assertThat(helperClasses)
        .containsExactlyInAnyOrder(
            TestHelperClasses.HelperSuperClass.class.getName(),
            TestHelperClasses.HelperInterface.class.getName(),
            TestHelperClasses.Helper.class.getName(),
            OtherTestHelperClasses.Bar.class.getName(),
            OtherTestHelperClasses.Foo.class.getName(),
            OtherTestHelperClasses.TestEnum.class.getName(),
            OtherTestHelperClasses.TestEnum.class.getName() + "$1",
            OtherTestHelperClasses.class.getName() + "$1");
  }

  @Test
  public void shouldCorrectlyFindExternalInstrumentationClasses() {
    ReferenceCollector collector =
        new ReferenceCollector(s -> s.startsWith("external.instrumentation"));
    collector.collectReferencesFromAdvice(
        TestClasses.ExternalInstrumentationAdvice.class.getName());
    collector.prune();

    Map<String, ClassRef> references = collector.getReferences();
    assertThat(references.get("external.NotInstrumentation")).isNotNull();

    List<String> helperClasses = collector.getSortedHelperClasses();
    assertThat(helperClasses).containsExactly(ExternalHelper.class.getName());
  }

  @ParameterizedTest
  @MethodSource
  public void shouldCollectHelperClassesFromResourceFile(
      @SuppressWarnings("unused") String desc, String resource) {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromResource(HelperResource.create(resource, resource));
    collector.prune();

    List<String> helperClasses = collector.getSortedHelperClasses();
    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                TestHelperClasses.HelperInterface.class.getName(),
                TestHelperClasses.Helper.class.getName()));
    assertThat(helperClasses)
        .containsSubsequence(
            Arrays.asList(
                TestHelperClasses.HelperSuperClass.class.getName(),
                TestHelperClasses.Helper.class.getName()));
  }

  @SuppressWarnings("unused")
  private static List<Arguments> shouldCollectHelperClassesFromResourceFile() {
    return Arrays.asList(
        Arguments.of("Java SPI", "META-INF/services/test.resource.file"),
        Arguments.of(
            "AWS SDK v2 global interceptors file",
            "software/amazon/awssdk/global/handlers/execution.interceptors"),
        Arguments.of(
            "AWS SDK v2 service interceptors file",
            "software/amazon/awssdk/services/testservice/execution.interceptors"),
        Arguments.of(
            "AWS SDK v2 service (second level) interceptors file",
            "software/amazon/awssdk/services/testservice/testsubservice/execution.interceptors"),
        Arguments.of(
            "AWS SDK v1 global interceptors file",
            "com/amazonaws/global/handlers/request.handler2s"),
        Arguments.of(
            "AWS SDK v1 service interceptors file",
            "com/amazonaws/services/testservice/request.handler2s"),
        Arguments.of(
            "AWS SDK v1 service (second level) interceptors file",
            "com/amazonaws/services/testservice/testsubservice/request.handler2s"));
  }

  @Test
  public void shouldIgnoreArbitraryResourceFile() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromResource(
        HelperResource.create("application.properties", "application.properties"));
    collector.prune();

    assertThat(collector.getReferences()).isEmpty();
    assertThat(collector.getSortedHelperClasses()).isEmpty();
  }

  @Test
  public void shouldCollectVirtualFields() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(VirtualFieldTestClasses.ValidAdvice.class.getName());
    collector.prune();

    VirtualFieldMappings virtualFieldMappings = collector.getVirtualFieldMappings();
    assertThat(virtualFieldMappings.entrySet())
        .containsExactlyInAnyOrder(
            entry(VirtualFieldTestClasses.Key1.class.getName(), Context.class.getName()),
            entry(VirtualFieldTestClasses.Key2.class.getName(), Context.class.getName()));
  }

  @Test
  public void shouldCollectMultipleVirtualFieldsForSingleClass() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(
        VirtualFieldTestClasses.TwoVirtualFieldsInTheSameClassAdvice.class.getName());
    collector.prune();

    VirtualFieldMappings virtualFieldMappings = collector.getVirtualFieldMappings();
    assertThat(virtualFieldMappings.entrySet())
        .containsExactlyInAnyOrder(
            entry(VirtualFieldTestClasses.Key1.class.getName(), Context.class.getName()),
            entry(VirtualFieldTestClasses.Key1.class.getName(), State.class.getName()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource
  public void shouldNotCollectVirtualFieldsForInvalidScenario(
      @SuppressWarnings("unused") String desc, String adviceClassName) {
    ReferenceCollector collector = new ReferenceCollector(s -> false);

    Assertions.assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(
            () -> {
              collector.collectReferencesFromAdvice(adviceClassName);
              collector.prune();
            });
  }

  @SuppressWarnings("unused")
  private static List<Arguments> shouldNotCollectVirtualFieldsForInvalidScenario() {
    return Arrays.asList(
        Arguments.of(
            "passing arbitrary variables or parameters to VirtualField.find()",
            VirtualFieldTestClasses.NotUsingClassRefAdvice.class.getName()),
        Arguments.of(
            "storing class ref in a local var",
            VirtualFieldTestClasses.PassingVariableAdvice.class.getName()),
        Arguments.of(
            "using array type as the field owner type",
            VirtualFieldTestClasses.UsingArrayAsOwnerAdvice.class.getName()),
        Arguments.of(
            "using primitive type as the field owner type",
            VirtualFieldTestClasses.UsingPrimitiveAsOwnerAdvice.class.getName()),
        Arguments.of(
            "using primitive type as the field type",
            VirtualFieldTestClasses.UsingPrimitiveAsFieldAdvice.class.getName()));
  }

  @Test
  public void shouldCollectArrayVirtualField() {
    ReferenceCollector collector = new ReferenceCollector(s -> false);
    collector.collectReferencesFromAdvice(
        VirtualFieldTestClasses.UsingArrayAsFieldAdvice.class.getName());
    collector.prune();

    VirtualFieldMappings virtualFieldMappings = collector.getVirtualFieldMappings();
    assertThat(virtualFieldMappings.entrySet())
        .containsExactly(
            entry(
                VirtualFieldTestClasses.Key1.class.getName(),
                Type.getType(Context[].class).getClassName()));
  }

  private static void assertHelperSuperClassMethod(ClassRef reference, boolean isAbstract) {
    assertMethod(
        reference,
        "abstractMethod",
        "()I",
        VisibilityFlag.PROTECTED,
        OwnershipFlag.NON_STATIC,
        isAbstract ? ManifestationFlag.ABSTRACT : ManifestationFlag.NON_FINAL);
  }

  private static void assertHelperInterfaceMethod(ClassRef reference, boolean isAbstract) {
    assertMethod(
        reference,
        "foo",
        "()V",
        VisibilityFlag.PUBLIC,
        OwnershipFlag.NON_STATIC,
        isAbstract ? ManifestationFlag.ABSTRACT : ManifestationFlag.NON_FINAL);
  }

  private static void assertMethod(
      ClassRef reference, String methodName, String methodDesc, Flag... flags) {
    MethodRef method = findMethod(reference, methodName, methodDesc);
    assertThat(method).isNotNull();
    assertThat(method.getFlags()).containsExactlyInAnyOrder(flags);
  }

  private static MethodRef findMethod(ClassRef reference, String methodName, String methodDesc) {
    for (MethodRef method : reference.getMethods()) {
      if (method.getName().equals(methodName) && method.getDescriptor().equals(methodDesc)) {
        return method;
      }
    }
    return null;
  }

  private static void assertField(ClassRef reference, String fieldName, Flag... flags) {
    FieldRef field = findField(reference, fieldName);
    assertThat(field).isNotNull();
    assertThat(field.getFlags()).containsExactly(flags);
  }

  private static FieldRef findField(ClassRef reference, String fieldName) {
    for (FieldRef field : reference.getFields()) {
      if (field.getName().equals(fieldName)) {
        return field;
      }
    }
    return null;
  }
}
