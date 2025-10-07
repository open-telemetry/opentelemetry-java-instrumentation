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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import muzzle.TestClasses;
import muzzle.TestClasses.Nested;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Type;

class ReferenceMatcherTest {
  private static final String TEST_EXTERNAL_INSTRUMENTATION_PACKAGE = "com.external.otel.instrumentation";

  private static ClassLoader safeClasspath;
  private static ClassLoader unsafeClasspath;

  @BeforeAll
  static void setup() throws Exception {
    safeClasspath = new URLClassLoader(
        new URL[] {
          ClasspathUtils.createJarWithClasses(
              Nested.A.class,
              Nested.B.class,
              Nested.SomeInterface.class,
              Nested.SomeImplementation.class)
        },
        null);

    unsafeClasspath = new URLClassLoader(
        new URL[] {
          ClasspathUtils.createJarWithClasses(
              Nested.A.class,
              Nested.SomeInterface.class,
              Nested.SomeImplementation.class)
        },
        null);
  }

  @Test
  void matchSafeClasspaths() {
    ReferenceCollector collector = new ReferenceCollector(className -> false);
    collector.collectReferencesFromAdvice(TestClasses.MethodBodyAdvice.class.getName());
    ReferenceMatcher refMatcher = createMatcher(collector.getReferences());

    assertThat(getMismatchClassSet(refMatcher.getMismatchedReferenceSources(safeClasspath))).isEmpty();
    assertThat(getMismatchClassSet(refMatcher.getMismatchedReferenceSources(unsafeClasspath)))
        .containsExactly(Mismatch.MissingClass.class);
  }

  @Test
  void matchingDoesNotHoldStrongReferenceToClassloaders() {
    try {
      assertThat(MuzzleWeakReferenceTestUtil.classLoaderRefIsGarbageCollected()).isTrue();
    } catch (InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
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
    CountingClassLoader cl = new CountingClassLoader(
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

  @ParameterizedTest
  @CsvSource({
    "muzzle.TestClasses$Nested$B, NON_INTERFACE, ''",
    "muzzle.TestClasses$Nested$B, INTERFACE, MissingFlag"
  })
  void matchingRef(String referenceName, String referenceFlag, String expectedMismatch) {
    Flag flag = "NON_INTERFACE".equals(referenceFlag) ? NON_INTERFACE : INTERFACE;
    ClassRef ref = ClassRef.builder(referenceName)
        .addFlag(flag)
        .build();

    List<Mismatch> mismatches = createMatcher(Collections.singletonMap(ref.getClassName(), ref))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    if (expectedMismatch.isEmpty()) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty();
    } else {
      assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingFlag.class);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "method, (Ljava/lang/String;)Ljava/lang/String;, '', muzzle.TestClasses$Nested$B, ''",
    "hashCode, ()I, '', muzzle.TestClasses$Nested$B, ''",
    "someMethod, ()V, '', muzzle.TestClasses$Nested$SomeInterface, ''",
    "privateStuff, ()V, PRIVATE_OR_HIGHER, muzzle.TestClasses$Nested$B, ''",
    "privateStuff, ()V, PROTECTED_OR_HIGHER, muzzle.TestClasses$Nested$B2, MissingFlag",
    "staticMethod, ()V, NON_STATIC, muzzle.TestClasses$Nested$B, MissingFlag",
    "missingMethod, ()V, '', muzzle.TestClasses$Nested$B, MissingMethod"
  })
  void methodMatch(String methodName, String methodDesc, String methodFlagsStr, String classToCheckName, String expectedMismatch)
      throws ClassNotFoundException {
    Type methodType = Type.getMethodType(methodDesc);
    List<Flag> methodFlags = parseMethodFlags(methodFlagsStr);
    Class<?> classToCheck = Class.forName(classToCheckName);

    ClassRef reference = ClassRef.builder(classToCheck.getName())
        .addMethod(new Source[0], methodFlags.toArray(new Flag[0]), methodName, methodType.getReturnType(), methodType.getArgumentTypes())
        .build();

    List<Mismatch> mismatches = createMatcher(Collections.singletonMap(reference.getClassName(), reference))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    if (expectedMismatch.isEmpty()) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty();
    } else {
      Class<? extends Mismatch> expectedMismatchClass = getMismatchClass(expectedMismatch);
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatchClass);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "missingField, Ljava/lang/String;, '', muzzle.TestClasses$Nested$A, MissingField",
    "privateField, Ljava/lang/String;, '', muzzle.TestClasses$Nested$A, MissingField",
    "privateField, Ljava/lang/Object;, PRIVATE_OR_HIGHER, muzzle.TestClasses$Nested$A, ''",
    "privateField, Ljava/lang/Object;, PROTECTED_OR_HIGHER, muzzle.TestClasses$Nested$A2, MissingFlag",
    "protectedField, Ljava/lang/Object;, STATIC, muzzle.TestClasses$Nested$A, MissingFlag",
    "staticB, Lmuzzle/TestClasses$Nested$B;, STATIC|PROTECTED_OR_HIGHER, muzzle.TestClasses$Nested$A, ''",
    "number, I, PACKAGE_OR_HIGHER, muzzle.TestClasses$Nested$Primitives, ''",
    "flag, Z, PACKAGE_OR_HIGHER, muzzle.TestClasses$Nested$Primitives, ''"
  })
  void fieldMatch(String fieldName, String fieldType, String fieldFlagsStr, String classToCheckName, String expectedMismatch)
      throws ClassNotFoundException {
    List<Flag> fieldFlags = parseFieldFlags(fieldFlagsStr);
    Class<?> classToCheck = Class.forName(classToCheckName);

    ClassRef reference = ClassRef.builder(classToCheck.getName())
        .addField(new Source[0], fieldFlags.toArray(new Flag[0]), fieldName, Type.getType(fieldType), false)
        .build();

    List<Mismatch> mismatches = createMatcher(Collections.singletonMap(reference.getClassName(), reference))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    if (expectedMismatch.isEmpty()) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty();
    } else {
      Class<? extends Mismatch> expectedMismatchClass = getMismatchClass(expectedMismatch);
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatchClass);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldNotCheckAbstractHelperClasses(String className) {
    ClassRef reference = ClassRef.builder(className)
        .setSuperClassName(TestHelperClasses.HelperSuperClass.class.getName())
        .addFlag(ABSTRACT)
        .addMethod(new Source[0], new Flag[] {ABSTRACT}, "unimplemented", Type.VOID_TYPE)
        .build();

    List<Mismatch> mismatches = createMatcher(
        Collections.singletonMap(reference.getClassName(), reference),
        Collections.singletonList(reference.getClassName()))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldNotCheckHelperClassesWithNoSupertypes(String className) {
    ClassRef reference = ClassRef.builder(className)
        .setSuperClassName(Object.class.getName())
        .addMethod(new Source[0], new Flag[0], "someMethod", Type.VOID_TYPE)
        .build();

    List<Mismatch> mismatches = createMatcher(
        Collections.singletonMap(reference.getClassName(), reference),
        Collections.singletonList(reference.getClassName()))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldFailHelperClassesThatDoesNotImplementAllAbstractMethods(String className) {
    ClassRef reference = ClassRef.builder(className)
        .setSuperClassName(TestAbstractSuperClass.class.getName())
        .addMethod(new Source[0], new Flag[0], "someMethod", Type.VOID_TYPE)
        .build();

    List<Mismatch> mismatches = createMatcher(
        Collections.singletonMap(reference.getClassName(), reference),
        Collections.singletonList(reference.getClassName()))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldFailHelperClassesThatDoNotImplementAllAbstractMethodsEvenIfEmptyAbstractClassReferenceExists(String className) {
    ClassRef emptySuperClassRef = ClassRef.builder(TestAbstractSuperClass.class.getName())
        .build();
    ClassRef reference = ClassRef.builder(className)
        .setSuperClassName(TestAbstractSuperClass.class.getName())
        .addMethod(new Source[0], new Flag[0], "someMethod", Type.VOID_TYPE)
        .build();

    Map<String, ClassRef> references = new HashMap<>();
    references.put(reference.getClassName(), reference);
    references.put(emptySuperClassRef.getClassName(), emptySuperClassRef);

    List<String> helperClasses = Arrays.asList(reference.getClassName(), emptySuperClassRef.getClassName());

    List<Mismatch> mismatches = createMatcher(references, helperClasses)
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldCheckHelperClassWhetherInterfaceMethodsAreImplementedInTheSuperClass(String className) {
    ClassRef baseHelper = ClassRef.builder("io.opentelemetry.instrumentation.BaseHelper")
        .setSuperClassName(Object.class.getName())
        .addInterfaceName(TestInterface.class.getName())
        .addMethod(new Source[0], new Flag[0], "foo", Type.VOID_TYPE)
        .build();
    // abstract HelperInterface#foo() is implemented by BaseHelper
    ClassRef helper = ClassRef.builder(className)
        .setSuperClassName(baseHelper.getClassName())
        .addInterfaceName(AnotherTestInterface.class.getName())
        .addMethod(new Source[0], new Flag[0], "bar", Type.VOID_TYPE)
        .build();

    Map<String, ClassRef> references = new HashMap<>();
    references.put(helper.getClassName(), helper);
    references.put(baseHelper.getClassName(), baseHelper);

    List<String> helperClasses = Arrays.asList(helper.getClassName(), baseHelper.getClassName());

    List<Mismatch> mismatches = createMatcher(references, helperClasses)
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldCheckHelperClassWhetherUsedFieldsAreDeclaredInTheSuperClass(String className) {
    ClassRef helper = ClassRef.builder(className)
        .setSuperClassName(LibraryBaseClass.class.getName())
        .addField(new Source[0], new Flag[0], "field", Type.getType("Ljava/lang/Integer;"), false)
        .build();

    List<Mismatch> mismatches = createMatcher(
        Collections.singletonMap(helper.getClassName(), helper),
        Collections.singletonList(helper.getClassName()))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(mismatches).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "io.opentelemetry.instrumentation.Helper, differentField, Ljava/lang/Integer;",
    "io.opentelemetry.instrumentation.Helper, field, Lcom/external/DifferentType;",
    "com.external.otel.instrumentation.Helper, differentField, Ljava/lang/Integer;",
    "com.external.otel.instrumentation.Helper, field, Lcom/external/DifferentType;"
  })
  void shouldFailHelperClassWhenItUsesFieldsUndeclaredInTheSuperClass(String className, String fieldName, String fieldType) {
    ClassRef helper = ClassRef.builder(className)
        .setSuperClassName(io.opentelemetry.javaagent.tooling.muzzle.DeclaredFieldTestClass.LibraryBaseClass.class.getName())
        .addField(new Source[0], new Flag[0], fieldName, Type.getType(fieldType), false)
        .build();

    List<Mismatch> mismatches = createMatcher(
        Collections.singletonMap(helper.getClassName(), helper),
        Collections.singletonList(helper.getClassName()))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingField.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"})
  void shouldFailHelperClassWhenTheLibraryParentClassHasDifferentConstructor(String className) {
    ClassRef helper = ClassRef.builder(className)
        .setSuperClassName(TestClasses.BaseClassWithConstructor.class.getName())
        .build();
    // muzzle codegen plugin has captured a no-arg constructor reference;
    // the actual constructor of the base class on the classpath requires a long
    ClassRef baseClassRef = ClassRef.builder(TestClasses.BaseClassWithConstructor.class.getName())
        .addMethod(new Source[0], new Flag[0], "<init>", Type.VOID_TYPE)
        .build();

    Map<String, ClassRef> references = new HashMap<>();
    references.put(helper.getClassName(), helper);
    references.put(baseClassRef.getClassName(), baseClassRef);

    List<Mismatch> mismatches = createMatcher(references, Collections.singletonList(helper.getClassName()))
        .getMismatchedReferenceSources(this.getClass().getClassLoader());

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod.class);
  }

  private static ReferenceMatcher createMatcher(Map<String, ClassRef> references) {
    return createMatcher(references, Collections.emptyList());
  }

  private static ReferenceMatcher createMatcher(Map<String, ClassRef> references, List<String> helperClasses) {
    return new ReferenceMatcher(helperClasses, references, className -> className.startsWith(TEST_EXTERNAL_INSTRUMENTATION_PACKAGE));
  }

  private static Set<Class<? extends Mismatch>> getMismatchClassSet(List<Mismatch> mismatches) {
    Set<Class<? extends Mismatch>> mismatchClasses = new HashSet<>(mismatches.size());
    for (Mismatch mismatch : mismatches) {
      mismatchClasses.add(mismatch.getClass());
    }
    return mismatchClasses;
  }

  private static List<Flag> parseMethodFlags(String flagsStr) {
    if (flagsStr.isEmpty()) {
      return Collections.emptyList();
    }
    List<Flag> flags = new ArrayList<>();
    if (flagsStr.contains("PRIVATE_OR_HIGHER")) {
      flags.add(PRIVATE_OR_HIGHER);
    }
    if (flagsStr.contains("PROTECTED_OR_HIGHER")) {
      flags.add(PROTECTED_OR_HIGHER);
    }
    if (flagsStr.contains("NON_STATIC")) {
      flags.add(NON_STATIC);
    }
    return flags;
  }

  private static List<Flag> parseFieldFlags(String flagsStr) {
    if (flagsStr.isEmpty()) {
      return Collections.emptyList();
    }
    List<Flag> flags = new ArrayList<>();
    if (flagsStr.contains("PRIVATE_OR_HIGHER")) {
      flags.add(PRIVATE_OR_HIGHER);
    }
    if (flagsStr.contains("PROTECTED_OR_HIGHER")) {
      flags.add(PROTECTED_OR_HIGHER);
    }
    if (flagsStr.contains("PACKAGE_OR_HIGHER")) {
      flags.add(PACKAGE_OR_HIGHER);
    }
    if (flagsStr.contains("STATIC")) {
      flags.add(STATIC);
    }
    return flags;
  }

  private static Class<? extends Mismatch> getMismatchClass(String mismatchName) {
    switch (mismatchName) {
      case "MissingFlag":
        return Mismatch.MissingFlag.class;
      case "MissingMethod":
        return Mismatch.MissingMethod.class;
      case "MissingField":
        return Mismatch.MissingField.class;
      default:
        throw new IllegalArgumentException("Unknown mismatch: " + mismatchName);
    }
  }
}
