/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag.ABSTRACT;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag.INTERFACE;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag.NON_INTERFACE;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PACKAGE_OR_HIGHER;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PRIVATE_OR_HIGHER;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PROTECTED_OR_HIGHER;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.OwnershipFlag.NON_STATIC;
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.OwnershipFlag.STATIC;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import external.LibraryBaseClass;
import io.opentelemetry.instrumentation.TestHelperClasses;
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils;
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import io.opentelemetry.test.AnotherTestInterface;
import io.opentelemetry.test.TestAbstractSuperClass;
import io.opentelemetry.test.TestInterface;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import muzzle.TestClasses;
import muzzle.TestClasses.Nested;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Type;

class ReferenceMatcherTest {
  private static final String TEST_EXTERNAL_INSTRUMENTATION_PACKAGE =
      "com.external.otel.instrumentation";

  private static ClassLoader safeClasspath;
  private static ClassLoader unsafeClasspath;

  @BeforeAll
  static void setup() throws Exception {
    safeClasspath =
        new URLClassLoader(
            new URL[] {
              ClasspathUtils.createJarWithClasses(
                  Nested.A.class,
                  Nested.B.class,
                  Nested.SomeInterface.class,
                  Nested.SomeImplementation.class)
            },
            null);

    unsafeClasspath =
        new URLClassLoader(
            new URL[] {
              ClasspathUtils.createJarWithClasses(
                  Nested.A.class, Nested.SomeInterface.class, Nested.SomeImplementation.class)
            },
            null);
  }

  @Test
  void matchSafeClasspaths() {
    ReferenceCollector collector = new ReferenceCollector(className -> false);
    collector.collectReferencesFromAdvice(TestClasses.MethodBodyAdvice.class.getName());
    ReferenceMatcher refMatcher = createMatcher(collector.getReferences());

    assertThat(getMismatchClassSet(refMatcher.getMismatchedReferenceSources(safeClasspath)))
        .isEmpty();
    assertThat(getMismatchClassSet(refMatcher.getMismatchedReferenceSources(unsafeClasspath)))
        .containsExactly(Mismatch.MissingClass.class);
  }

  @Test
  void matchingDoesNotHoldStrongReferenceToClassloaders()
      throws InterruptedException, TimeoutException {
    assertThat(MuzzleWeakReferenceTestUtil.classLoaderRefIsGarbageCollected()).isTrue();
  }

  private static class CountingClassLoader extends URLClassLoader {
    int count = 0;

    CountingClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, parent);
    }

    @Override
    public URL getResource(String name) {
      count++;
      return super.getResource(name);
    }
  }

  @Test
  void muzzleTypePoolCaches() throws Exception {
    CountingClassLoader cl =
        new CountingClassLoader(
            new URL[] {
              ClasspathUtils.createJarWithClasses(
                  Nested.A.class,
                  Nested.B.class,
                  Nested.SomeInterface.class,
                  Nested.SomeImplementation.class)
            },
            null);

    ReferenceCollector collector = new ReferenceCollector(className -> false);
    collector.collectReferencesFromAdvice(Nested.class.getName());

    ReferenceMatcher refMatcher1 = createMatcher(collector.getReferences());
    ReferenceMatcher refMatcher2 = createMatcher(collector.getReferences());
    assertThat(getMismatchClassSet(refMatcher1.getMismatchedReferenceSources(cl))).isEmpty();
    int countAfterFirstMatch = cl.count;
    // the second matcher should be able to used cached type descriptions from the first
    assertThat(getMismatchClassSet(refMatcher2.getMismatchedReferenceSources(cl))).isEmpty();

    assertThat(cl.count).isEqualTo(countAfterFirstMatch);
  }

  private static Stream<Arguments> matchingRefProvider() {
    return Stream.of(
        Arguments.of(Nested.B.class, NON_INTERFACE, null),
        Arguments.of(Nested.B.class, INTERFACE, Mismatch.MissingFlag.class));
  }

  @ParameterizedTest
  @MethodSource("matchingRefProvider")
  void matchingRef(
      Class<?> referenceClass, Flag referenceFlag, Class<? extends Mismatch> expectedMismatch) {
    ClassRef ref = ClassRef.builder(referenceClass.getName()).addFlag(referenceFlag).build();

    List<Mismatch> mismatches =
        createMatcher(singletonMap(ref.getClassName(), ref))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    if (expectedMismatch == null) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty();
    } else {
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatch);
    }
  }

  private static Stream<Arguments> methodMatchProvider() {
    return Stream.of(
        Arguments.of(
            "match method declared in class",
            "method",
            Type.getMethodType(Type.getType(String.class), Type.getType(String.class)),
            emptySet(),
            Nested.B.class,
            null),
        Arguments.of(
            "match method declared in superclass",
            "hashCode",
            Type.getMethodType(Type.INT_TYPE),
            emptySet(),
            Nested.B.class,
            null),
        Arguments.of(
            "match method declared in interface",
            "someMethod",
            Type.getMethodType(Type.VOID_TYPE),
            emptySet(),
            Nested.SomeInterface.class,
            null),
        Arguments.of(
            "match private method",
            "privateStuff",
            Type.getMethodType(Type.VOID_TYPE),
            singleton(PRIVATE_OR_HIGHER),
            Nested.B.class,
            null),
        Arguments.of(
            "fail match private in supertype",
            "privateStuff",
            Type.getMethodType(Type.VOID_TYPE),
            singleton(PROTECTED_OR_HIGHER),
            Nested.B2.class,
            Mismatch.MissingFlag.class),
        Arguments.of(
            "static method mismatch",
            "staticMethod",
            Type.getMethodType(Type.VOID_TYPE),
            singleton(NON_STATIC),
            Nested.B.class,
            Mismatch.MissingFlag.class),
        Arguments.of(
            "missing method mismatch",
            "missingMethod",
            Type.getMethodType(Type.VOID_TYPE),
            emptySet(),
            Nested.B.class,
            Mismatch.MissingMethod.class));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("methodMatchProvider")
  void methodMatch(
      String testName,
      String methodName,
      Type methodType,
      Set<Flag> methodFlags,
      Class<?> classToCheck,
      Class<? extends Mismatch> expectedMismatch) {
    ClassRef reference =
        ClassRef.builder(classToCheck.getName())
            .addMethod(
                new Source[0],
                methodFlags.toArray(new Flag[0]),
                methodName,
                methodType.getReturnType(),
                methodType.getArgumentTypes())
            .build();

    List<Mismatch> mismatches =
        createMatcher(singletonMap(reference.getClassName(), reference))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    if (expectedMismatch == null) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty();
    } else {
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatch);
    }
  }

  private static Stream<Arguments> fieldMatchProvider() {
    return Stream.of(
        Arguments.of(
            "mismatch missing field",
            "missingField",
            Type.getType(String.class),
            emptySet(),
            Nested.A.class,
            Mismatch.MissingField.class),
        Arguments.of(
            "mismatch field type signature",
            "privateField",
            Type.getType(String.class),
            emptySet(),
            Nested.A.class,
            Mismatch.MissingField.class),
        Arguments.of(
            "match private field",
            "privateField",
            Type.getType(Object.class),
            singleton(PRIVATE_OR_HIGHER),
            Nested.A.class,
            null),
        Arguments.of(
            "mismatch private field in supertype",
            "privateField",
            Type.getType(Object.class),
            singleton(PROTECTED_OR_HIGHER),
            Nested.A2.class,
            Mismatch.MissingFlag.class),
        Arguments.of(
            "mismatch static field",
            "protectedField",
            Type.getType(Object.class),
            singleton(STATIC),
            Nested.A.class,
            Mismatch.MissingFlag.class),
        Arguments.of(
            "match static field",
            "staticB",
            Type.getType(Nested.B.class),
            new HashSet<>(Arrays.asList(STATIC, PROTECTED_OR_HIGHER)),
            Nested.A.class,
            null),
        Arguments.of(
            "match primitive int",
            "number",
            Type.INT_TYPE,
            singleton(PACKAGE_OR_HIGHER),
            Nested.Primitives.class,
            null),
        Arguments.of(
            "match primitive boolean",
            "flag",
            Type.BOOLEAN_TYPE,
            singleton(PACKAGE_OR_HIGHER),
            Nested.Primitives.class,
            null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("fieldMatchProvider")
  void fieldMatch(
      String testName,
      String fieldName,
      Type fieldType,
      Set<Flag> fieldFlags,
      Class<?> classToCheck,
      Class<? extends Mismatch> expectedMismatch) {
    ClassRef reference =
        ClassRef.builder(classToCheck.getName())
            .addField(new Source[0], fieldFlags.toArray(new Flag[0]), fieldName, fieldType, false)
            .build();

    List<Mismatch> mismatches =
        createMatcher(singletonMap(reference.getClassName(), reference))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    if (expectedMismatch == null) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty();
    } else {
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatch);
    }
  }

  private static Stream<Arguments> helperClassNames() {
    return Stream.of(
        Arguments.of("io.opentelemetry.instrumentation.Helper"),
        Arguments.of("com.external.otel.instrumentation.Helper"));
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void shouldNotCheckAbstractHelperClasses(String className) {
    ClassRef reference =
        ClassRef.builder(className)
            .setSuperClassName(TestHelperClasses.HelperSuperClass.class.getName())
            .addFlag(ABSTRACT)
            .addMethod(new Source[0], new Flag[] {ABSTRACT}, "unimplemented", Type.VOID_TYPE)
            .build();

    List<Mismatch> mismatches =
        createMatcher(
                singletonMap(reference.getClassName(), reference),
                singletonList(reference.getClassName()))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void shouldNotCheckHelperClassesWithNoSupertypes(String className) {
    ClassRef reference =
        ClassRef.builder(className)
            .setSuperClassName(Object.class.getName())
            .addMethod(new Source[0], new Flag[0], "someMethod", Type.VOID_TYPE)
            .build();

    List<Mismatch> mismatches =
        createMatcher(
                singletonMap(reference.getClassName(), reference),
                singletonList(reference.getClassName()))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void shouldFailHelperClassesThatDoNotImplementAllAbstractMethods(String className) {
    ClassRef reference =
        ClassRef.builder(className)
            .setSuperClassName(TestAbstractSuperClass.class.getName())
            .addMethod(new Source[0], new Flag[0], "someMethod", Type.VOID_TYPE)
            .build();

    List<Mismatch> mismatches =
        createMatcher(
                singletonMap(reference.getClassName(), reference),
                singletonList(reference.getClassName()))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod.class);
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void
      shouldFailHelperClassesThatDoNotImplementAllAbstractMethodsEvenIfEmptyAbstractClassReferenceExists(
          String className) {
    ClassRef emptySuperClassRef = ClassRef.builder(TestAbstractSuperClass.class.getName()).build();
    ClassRef reference =
        ClassRef.builder(className)
            .setSuperClassName(TestAbstractSuperClass.class.getName())
            .addMethod(new Source[0], new Flag[0], "someMethod", Type.VOID_TYPE)
            .build();

    Map<String, ClassRef> references = new HashMap<>();
    references.put(reference.getClassName(), reference);
    references.put(emptySuperClassRef.getClassName(), emptySuperClassRef);

    List<String> helperClasses =
        Arrays.asList(reference.getClassName(), emptySuperClassRef.getClassName());

    List<Mismatch> mismatches =
        createMatcher(references, helperClasses)
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod.class);
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void shouldCheckHelperClassWhetherInterfaceMethodsAreImplementedInTheSuperClass(
      String className) {
    ClassRef baseHelper =
        ClassRef.builder("io.opentelemetry.instrumentation.BaseHelper")
            .setSuperClassName(Object.class.getName())
            .addInterfaceName(TestInterface.class.getName())
            .addMethod(new Source[0], new Flag[0], "foo", Type.VOID_TYPE)
            .build();
    // abstract HelperInterface#foo() is implemented by BaseHelper
    ClassRef helper =
        ClassRef.builder(className)
            .setSuperClassName(baseHelper.getClassName())
            .addInterfaceName(AnotherTestInterface.class.getName())
            .addMethod(new Source[0], new Flag[0], "bar", Type.VOID_TYPE)
            .build();

    Map<String, ClassRef> references = new HashMap<>();
    references.put(helper.getClassName(), helper);
    references.put(baseHelper.getClassName(), baseHelper);

    List<String> helperClasses = Arrays.asList(helper.getClassName(), baseHelper.getClassName());

    List<Mismatch> mismatches =
        createMatcher(references, helperClasses)
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void shouldCheckHelperClassWhetherUsedFieldsAreDeclaredInTheSuperClass(String className) {
    ClassRef helper =
        ClassRef.builder(className)
            .setSuperClassName(LibraryBaseClass.class.getName())
            .addField(
                new Source[0], new Flag[0], "field", Type.getType("Ljava/lang/Integer;"), false)
            .build();

    List<Mismatch> mismatches =
        createMatcher(
                singletonMap(helper.getClassName(), helper), singletonList(helper.getClassName()))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = '|',
      value = {
        "internal helper, different field name | io.opentelemetry.instrumentation.Helper | differentField | Ljava/lang/Integer;",
        "internal helper, different field type | io.opentelemetry.instrumentation.Helper | field | Lcom/external/DifferentType;",
        "external helper, different field name | com.external.otel.instrumentation.Helper | differentField | Ljava/lang/Integer;",
        "external helper, different field type | com.external.otel.instrumentation.Helper | field | Lcom/external/DifferentType;"
      })
  void shouldFailHelperClassWhenItUsesFieldsUndeclaredInTheSuperClass(
      String testName, String className, String fieldName, String fieldType) {
    ClassRef helper =
        ClassRef.builder(className)
            .setSuperClassName(
                io.opentelemetry.javaagent.tooling.muzzle.DeclaredFieldTestClass.LibraryBaseClass
                    .class
                    .getName())
            .addField(new Source[0], new Flag[0], fieldName, Type.getType(fieldType), false)
            .build();

    List<Mismatch> mismatches =
        createMatcher(
                singletonMap(helper.getClassName(), helper), singletonList(helper.getClassName()))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingField.class);
  }

  @ParameterizedTest
  @MethodSource("helperClassNames")
  void shouldFailHelperClassWhenTheLibraryParentClassHasDifferentConstructor(String className) {
    ClassRef helper =
        ClassRef.builder(className)
            .setSuperClassName(TestClasses.BaseClassWithConstructor.class.getName())
            .build();
    // muzzle codegen plugin has captured a no-arg constructor reference;
    // the actual constructor of the base class on the classpath requires a long
    ClassRef baseClassRef =
        ClassRef.builder(TestClasses.BaseClassWithConstructor.class.getName())
            .addMethod(new Source[0], new Flag[0], "<init>", Type.VOID_TYPE)
            .build();

    Map<String, ClassRef> references = new HashMap<>();
    references.put(helper.getClassName(), helper);
    references.put(baseClassRef.getClassName(), baseClassRef);

    List<Mismatch> mismatches =
        createMatcher(references, singletonList(helper.getClassName()))
            .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod.class);
  }

  private static ReferenceMatcher createMatcher(Map<String, ClassRef> references) {
    return createMatcher(references, Collections.emptyList());
  }

  private static ReferenceMatcher createMatcher(
      Map<String, ClassRef> references, List<String> helperClasses) {
    return new ReferenceMatcher(
        helperClasses,
        references,
        className -> className.startsWith(TEST_EXTERNAL_INSTRUMENTATION_PACKAGE));
  }

  private static Set<Class<? extends Mismatch>> getMismatchClassSet(List<Mismatch> mismatches) {
    Set<Class<? extends Mismatch>> mismatchClasses = new HashSet<>(mismatches.size());
    for (Mismatch mismatch : mismatches) {
      mismatchClasses.add(mismatch.getClass());
    }
    return mismatchClasses;
  }
}
