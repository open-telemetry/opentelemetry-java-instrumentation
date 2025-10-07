/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle

import static org.assertj.core.api.Assertions.assertThat

import external.LibraryBaseClass
import io.opentelemetry.instrumentation.TestHelperClasses
import io.opentelemetry.instrumentation.test.utils.ClasspathUtils
import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag
import io.opentelemetry.javaagent.tooling.muzzle.references.Source
import io.opentelemetry.test.AnotherTestInterface
import io.opentelemetry.test.TestAbstractSuperClass
import io.opentelemetry.test.TestInterface
import muzzle.TestClasses
import muzzle.TestClasses.Nested
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.objectweb.asm.Type

import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag.ABSTRACT
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag.INTERFACE
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.ManifestationFlag.NON_INTERFACE
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PACKAGE_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PRIVATE_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.MinimumVisibilityFlag.PROTECTED_OR_HIGHER
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.OwnershipFlag.NON_STATIC
import static io.opentelemetry.javaagent.tooling.muzzle.references.Flag.OwnershipFlag.STATIC

class ReferenceMatcherTest {
  static final TEST_EXTERNAL_INSTRUMENTATION_PACKAGE = "com.external.otel.instrumentation"

  static ClassLoader safeClasspath
  static ClassLoader unsafeClasspath

  @BeforeAll
  static void setup() {
    safeClasspath = new URLClassLoader([ClasspathUtils.createJarWithClasses(Nested.A,
      Nested.B,
      Nested.SomeInterface,
      Nested.SomeImplementation)] as URL[],
      (ClassLoader) null)

    unsafeClasspath = new URLClassLoader([ClasspathUtils.createJarWithClasses(Nested.A,
      Nested.SomeInterface,
      Nested.SomeImplementation)] as URL[],
      (ClassLoader) null)
  }

  @Test
  void matchSafeClasspaths() {
    def collector = new ReferenceCollector({ false })
    collector.collectReferencesFromAdvice(TestClasses.MethodBodyAdvice.name)
    def refMatcher = createMatcher(collector.getReferences())

    assertThat(getMismatchClassSet(refMatcher.getMismatchedReferenceSources(safeClasspath))).isEmpty()
    assertThat(getMismatchClassSet(refMatcher.getMismatchedReferenceSources(unsafeClasspath)))
        .containsExactly(Mismatch.MissingClass)
  }

  @Test
  void matchingDoesNotHoldStrongReferenceToClassloaders() {
    assertThat(MuzzleWeakReferenceTestUtil.classLoaderRefIsGarbageCollected()).isTrue()
  }

  private static class CountingClassLoader extends URLClassLoader {
    int count = 0

    CountingClassLoader(URL[] urls, ClassLoader parent) {
      super(urls, (ClassLoader) parent)
    }

    @Override
    URL getResource(String name) {
      count++
      return super.getResource(name)
    }
  }

  @Test
  void muzzleTypePoolCaches() {
    def cl = new CountingClassLoader(
      [ClasspathUtils.createJarWithClasses(Nested.A,
        Nested.B,
        Nested.SomeInterface,
        Nested.SomeImplementation)] as URL[],
      (ClassLoader) null)

    def collector = new ReferenceCollector({ false })
    collector.collectReferencesFromAdvice(Nested.name)

    def refMatcher1 = createMatcher(collector.getReferences())
    def refMatcher2 = createMatcher(collector.getReferences())
    assertThat(getMismatchClassSet(refMatcher1.getMismatchedReferenceSources(cl))).isEmpty()
    int countAfterFirstMatch = cl.count
    // the second matcher should be able to used cached type descriptions from the first
    assertThat(getMismatchClassSet(refMatcher2.getMismatchedReferenceSources(cl))).isEmpty()

    assertThat(cl.count).isEqualTo(countAfterFirstMatch)
  }

  @ParameterizedTest
  @CsvSource([
    "muzzle.TestClasses\$Nested\$B, NON_INTERFACE, ''",
    "muzzle.TestClasses\$Nested\$B, INTERFACE, MissingFlag"
  ])
  void matchingRef(String referenceName, String referenceFlag, String expectedMismatch) {
    def flag = referenceFlag == "NON_INTERFACE" ? NON_INTERFACE : INTERFACE
    def ref = ClassRef.builder(referenceName)
      .addFlag(flag)
      .build()

    def mismatches = createMatcher([(ref.className): ref]).getMismatchedReferenceSources(this.class.classLoader)

    if (expectedMismatch.isEmpty()) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty()
    } else {
      assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingFlag)
    }
  }

  @ParameterizedTest
  @CsvSource([
    "method, (Ljava/lang/String;)Ljava/lang/String;, '', muzzle.TestClasses\$Nested\$B, ''",
    "hashCode, ()I, '', muzzle.TestClasses\$Nested\$B, ''",
    "someMethod, ()V, '', muzzle.TestClasses\$Nested\$SomeInterface, ''",
    "privateStuff, ()V, PRIVATE_OR_HIGHER, muzzle.TestClasses\$Nested\$B, ''",
    "privateStuff, ()V, PROTECTED_OR_HIGHER, muzzle.TestClasses\$Nested\$B2, MissingFlag",
    "staticMethod, ()V, NON_STATIC, muzzle.TestClasses\$Nested\$B, MissingFlag",
    "missingMethod, ()V, '', muzzle.TestClasses\$Nested\$B, MissingMethod"
  ])
  void methodMatch(String methodName, String methodDesc, String methodFlagsStr, String classToCheckName, String expectedMismatch) {
    def methodType = Type.getMethodType(methodDesc)
    def methodFlags = parseMethodFlags(methodFlagsStr)
    def classToCheck = Class.forName(classToCheckName)

    def reference = ClassRef.builder(classToCheck.name)
      .addMethod(new Source[0], methodFlags as Flag[], methodName, methodType.returnType, methodType.argumentTypes)
      .build()

    def mismatches = createMatcher([(reference.className): reference])
      .getMismatchedReferenceSources(this.class.classLoader)

    if (expectedMismatch.isEmpty()) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty()
    } else {
      def expectedMismatchClass = getMismatchClass(expectedMismatch)
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatchClass)
    }
  }

  @ParameterizedTest
  @CsvSource([
    "missingField, Ljava/lang/String;, '', muzzle.TestClasses\$Nested\$A, MissingField",
    "privateField, Ljava/lang/String;, '', muzzle.TestClasses\$Nested\$A, MissingField",
    "privateField, Ljava/lang/Object;, PRIVATE_OR_HIGHER, muzzle.TestClasses\$Nested\$A, ''",
    "privateField, Ljava/lang/Object;, PROTECTED_OR_HIGHER, muzzle.TestClasses\$Nested\$A2, MissingFlag",
    "protectedField, Ljava/lang/Object;, STATIC, muzzle.TestClasses\$Nested\$A, MissingFlag",
    "staticB, Lmuzzle/TestClasses\$Nested\$B;, STATIC|PROTECTED_OR_HIGHER, muzzle.TestClasses\$Nested\$A, ''",
    "number, I, PACKAGE_OR_HIGHER, muzzle.TestClasses\$Nested\$Primitives, ''",
    "flag, Z, PACKAGE_OR_HIGHER, muzzle.TestClasses\$Nested\$Primitives, ''"
  ])
  void fieldMatch(String fieldName, String fieldType, String fieldFlagsStr, String classToCheckName, String expectedMismatch) {
    def fieldFlags = parseFieldFlags(fieldFlagsStr)
    def classToCheck = Class.forName(classToCheckName)

    def reference = ClassRef.builder(classToCheck.name)
      .addField(new Source[0], fieldFlags as Flag[], fieldName, Type.getType(fieldType), false)
      .build()

    def mismatches = createMatcher([(reference.className): reference])
      .getMismatchedReferenceSources(this.class.classLoader)

    if (expectedMismatch.isEmpty()) {
      assertThat(getMismatchClassSet(mismatches)).isEmpty()
    } else {
      def expectedMismatchClass = getMismatchClass(expectedMismatch)
      assertThat(getMismatchClassSet(mismatches)).containsExactly(expectedMismatchClass)
    }
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldNotCheckAbstractHelperClasses(String className) {
    def reference = ClassRef.builder(className)
      .setSuperClassName(TestHelperClasses.HelperSuperClass.name)
      .addFlag(ABSTRACT)
      .addMethod(new Source[0], [ABSTRACT] as Flag[], "unimplemented", Type.VOID_TYPE)
      .build()

    def mismatches = createMatcher([(reference.className): reference], [reference.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(mismatches).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldNotCheckHelperClassesWithNoSupertypes(String className) {
    def reference = ClassRef.builder(className)
      .setSuperClassName(Object.name)
      .addMethod(new Source[0], [] as Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    def mismatches = createMatcher([(reference.className): reference], [reference.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(mismatches).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldFailHelperClassesThatDoesNotImplementAllAbstractMethods(String className) {
    def reference = ClassRef.builder(className)
      .setSuperClassName(TestAbstractSuperClass.name)
      .addMethod(new Source[0], [] as Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    def mismatches = createMatcher([(reference.className): reference], [reference.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod)
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldFailHelperClassesThatDoNotImplementAllAbstractMethodsEvenIfEmptyAbstractClassReferenceExists(String className) {
    def emptySuperClassRef = ClassRef.builder(TestAbstractSuperClass.name)
      .build()
    def reference = ClassRef.builder(className)
      .setSuperClassName(TestAbstractSuperClass.name)
      .addMethod(new Source[0], [] as Flag[], "someMethod", Type.VOID_TYPE)
      .build()

    def mismatches = createMatcher(
      [(reference.className): reference, (emptySuperClassRef.className): emptySuperClassRef],
      [reference.className, emptySuperClassRef.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod)
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldCheckHelperClassWhetherInterfaceMethodsAreImplementedInTheSuperClass(String className) {
    def baseHelper = ClassRef.builder("io.opentelemetry.instrumentation.BaseHelper")
      .setSuperClassName(Object.name)
      .addInterfaceName(TestInterface.name)
      .addMethod(new Source[0], [] as Flag[], "foo", Type.VOID_TYPE)
      .build()
    // abstract HelperInterface#foo() is implemented by BaseHelper
    def helper = ClassRef.builder(className)
      .setSuperClassName(baseHelper.className)
      .addInterfaceName(AnotherTestInterface.name)
      .addMethod(new Source[0], [] as Flag[], "bar", Type.VOID_TYPE)
      .build()

    def mismatches = createMatcher(
      [(helper.className): helper, (baseHelper.className): baseHelper],
      [helper.className, baseHelper.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(mismatches).isEmpty()
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldCheckHelperClassWhetherUsedFieldsAreDeclaredInTheSuperClass(String className) {
    def helper = ClassRef.builder(className)
      .setSuperClassName(LibraryBaseClass.name)
      .addField(new Source[0], new Flag[0], "field", Type.getType("Ljava/lang/Integer;"), false)
      .build()

    def mismatches = createMatcher([(helper.className): helper], [helper.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(mismatches).isEmpty()
  }

  @ParameterizedTest
  @CsvSource([
    "io.opentelemetry.instrumentation.Helper, differentField, Ljava/lang/Integer;",
    "io.opentelemetry.instrumentation.Helper, field, Lcom/external/DifferentType;",
    "com.external.otel.instrumentation.Helper, differentField, Ljava/lang/Integer;",
    "com.external.otel.instrumentation.Helper, field, Lcom/external/DifferentType;"
  ])
  void shouldFailHelperClassWhenItUsesFieldsUndeclaredInTheSuperClass(String className, String fieldName, String fieldType) {
    def helper = ClassRef.builder(className)
      .setSuperClassName(DeclaredFieldTestClass.LibraryBaseClass.name)
      .addField(new Source[0], new Flag[0], fieldName, Type.getType(fieldType), false)
      .build()

    def mismatches = createMatcher([(helper.className): helper], [helper.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingField)
  }

  @ParameterizedTest
  @ValueSource(strings = ["io.opentelemetry.instrumentation.Helper", "com.external.otel.instrumentation.Helper"])
  void shouldFailHelperClassWhenTheLibraryParentClassHasDifferentConstructor(String className) {
    def helper = ClassRef.builder(className)
      .setSuperClassName(TestClasses.BaseClassWithConstructor.name)
      .build()
    // muzzle codegen plugin has captured a no-arg constructor reference;
    // the actual constructor of the base class on the classpath requires a long
    def baseClassRef = ClassRef.builder(TestClasses.BaseClassWithConstructor.name)
      .addMethod(new Source[0], new Flag[0], "<init>", Type.VOID_TYPE)
      .build()

    def mismatches = createMatcher([(helper.className): helper, (baseClassRef.className): baseClassRef], [helper.className])
      .getMismatchedReferenceSources(this.class.classLoader)

    assertThat(getMismatchClassSet(mismatches)).containsExactly(Mismatch.MissingMethod)
  }

  private static ReferenceMatcher createMatcher(Map<String, ClassRef> references = [:],
                                                List<String> helperClasses = []) {
    new ReferenceMatcher(helperClasses, references, { it.startsWith(TEST_EXTERNAL_INSTRUMENTATION_PACKAGE) })
  }

  private static Set<Class> getMismatchClassSet(List<Mismatch> mismatches) {
    Set<Class> mismatchClasses = new HashSet<>(mismatches.size())
    for (Mismatch mismatch : mismatches) {
      mismatchClasses.add(mismatch.class)
    }
    return mismatchClasses
  }

  private static List<Flag> parseMethodFlags(String flagsStr) {
    if (flagsStr.isEmpty()) return []
    def flags = []
    if (flagsStr.contains("PRIVATE_OR_HIGHER")) flags.add(PRIVATE_OR_HIGHER)
    if (flagsStr.contains("PROTECTED_OR_HIGHER")) flags.add(PROTECTED_OR_HIGHER)
    if (flagsStr.contains("NON_STATIC")) flags.add(NON_STATIC)
    return flags
  }

  private static List<Flag> parseFieldFlags(String flagsStr) {
    if (flagsStr.isEmpty()) return []
    def flags = []
    if (flagsStr.contains("PRIVATE_OR_HIGHER")) flags.add(PRIVATE_OR_HIGHER)
    if (flagsStr.contains("PROTECTED_OR_HIGHER")) flags.add(PROTECTED_OR_HIGHER)
    if (flagsStr.contains("PACKAGE_OR_HIGHER")) flags.add(PACKAGE_OR_HIGHER)
    if (flagsStr.contains("STATIC")) flags.add(STATIC)
    return flags
  }

  private static Class getMismatchClass(String mismatchName) {
    switch (mismatchName) {
      case "MissingFlag": return Mismatch.MissingFlag
      case "MissingMethod": return Mismatch.MissingMethod
      case "MissingField": return Mismatch.MissingField
      default: throw new IllegalArgumentException("Unknown mismatch: " + mismatchName)
    }
  }
}
