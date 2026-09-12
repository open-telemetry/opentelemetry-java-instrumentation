/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.opentelemetry.javaagent.tooling.muzzle.references.ClassRef;
import io.opentelemetry.javaagent.tooling.muzzle.references.Flag;
import io.opentelemetry.javaagent.tooling.muzzle.references.Source;
import java.util.HashSet;
import java.util.Set;
import muzzle.samepackage.SamePackageAccessTestClasses.AdviceEntryPoint;
import muzzle.samepackage.SamePackageAccessTestClasses.BadHelper;
import muzzle.samepackage.SamePackageAccessTestClasses.BadHelperAdvice;
import muzzle.samepackage.SamePackageAccessTestClasses.GoodHelper;
import muzzle.samepackage.SamePackageAccessTestClasses.GoodHelperAdvice;
import muzzle.samepackage.SamePackageAccessTestClasses.HelperCallingHelper;
import muzzle.samepackage.SamePackageAccessTestClasses.HelperCallingHelperAdvice;
import muzzle.samepackage.SamePackageAccessTestClasses.LibrarySubClassWithHiddenField;
import muzzle.samepackage.SamePackageAccessTestClasses.NonInlinedAdviceEntryPoint;
import muzzle.samepackage.SamePackageAccessTestClasses.OtherHelper;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

class SamePackageAccessValidatorTest {

  private static ReferenceCollector collectorForHelpers(String... helperClassNames) {
    Set<String> helpers = new HashSet<>(asList(helperClassNames));
    return new ReferenceCollector(helpers::contains);
  }

  private static ReferenceCollector collectBadHelper() {
    ReferenceCollector collector = collectorForHelpers(BadHelper.class.getName());
    collector.collectReferencesFromAdvice(BadHelperAdvice.class.getName());
    collector.prune();
    return collector;
  }

  @Test
  void rejectsNonPublicClass() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("PackagePrivateLibraryClass is not public");
  }

  @Test
  void rejectsNonPublicConstructor() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("constructor")
        .withMessageContaining("LibraryClass#<init>(I)V is not public");
  }

  @Test
  void rejectsNonPublicMethod() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("method")
        .withMessageContaining("LibraryClass#packagePrivateMethod()V is not public");
  }

  @Test
  void rejectsNonPublicField() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("field")
        .withMessageContaining("LibraryClass#packagePrivateField is not public");
  }

  @Test
  void rejectsProtectedMembers() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("LibraryClass#protectedMethod()V is not public")
        .withMessageContaining("LibraryClass#protectedField is not public");
  }

  @Test
  void rejectsInheritedProtectedMembers() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("LibrarySubClass#inheritedProtectedMethod()V is not public")
        .withMessageContaining("LibrarySubClass#inheritedProtectedField is not public");
  }

  @Test
  void resolvesInheritedFieldByNameAndDescriptor() {
    Source source = new Source(BadHelper.class.getName(), 1);
    ClassRef reference =
        ClassRef.builder(LibrarySubClassWithHiddenField.class.getName())
            .addField(
                new Source[] {source},
                new Flag[0],
                "hiddenField",
                Type.INT_TYPE,
                /* isFieldDeclared= */ false)
            .build();
    SamePackageAccessValidator validator =
        new SamePackageAccessValidator(
            new HelperClassPredicate(name -> name.equals(BadHelper.class.getName())),
            SamePackageAccessValidatorTest.class.getClassLoader());

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(
            () ->
                validator.validate(
                    singletonMap(LibrarySubClassWithHiddenField.class.getName(), reference)))
        .withMessageContaining("LibrarySubClassWithHiddenField#hiddenField is not public");
  }

  @Test
  void aggregatesAllViolationsInASingleException() {
    ReferenceCollector collector = collectBadHelper();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .satisfies(
            e -> {
              String message = e.getMessage();
              assertThat(message).contains("PackagePrivateLibraryClass is not public");
              assertThat(message).contains("LibraryClass#<init>(I)V is not public");
              assertThat(message).contains("LibraryClass#packagePrivateMethod()V is not public");
              assertThat(message).contains("LibraryClass#packagePrivateField is not public");
              assertThat(message).contains("LibraryClass#protectedMethod()V is not public");
              assertThat(message).contains("LibraryClass#protectedField is not public");
              assertThat(message)
                  .contains("LibrarySubClass#inheritedProtectedMethod()V is not public");
              assertThat(message).contains("LibrarySubClass#inheritedProtectedField is not public");
            });
  }

  @Test
  void acceptsPublicSamePackageAccess() {
    ReferenceCollector collector = collectorForHelpers(GoodHelper.class.getName());
    collector.collectReferencesFromAdvice(GoodHelperAdvice.class.getName());
    collector.prune();

    assertThatCode(collector::validateSamePackageLibraryAccess).doesNotThrowAnyException();
  }

  @Test
  void acceptsInlinedAdvice() {
    ReferenceCollector collector = collectorForHelpers();
    collector.collectReferencesFromAdvice(AdviceEntryPoint.class.getName());
    collector.prune();

    assertThatCode(collector::validateSamePackageLibraryAccess).doesNotThrowAnyException();
  }

  @Test
  void rejectsNonInlinedAdvice() {
    ReferenceCollector collector = collectorForHelpers();
    collector.collectReferencesFromAdvice(NonInlinedAdviceEntryPoint.class.getName());
    collector.prune();

    assertThatExceptionOfType(MuzzleCompilationException.class)
        .isThrownBy(collector::validateSamePackageLibraryAccess)
        .withMessageContaining("PackagePrivateLibraryClass is not public");
  }

  @Test
  void keepsInlineStateWhenSourceLocationsMatch() {
    assertThat(new Source(AdviceEntryPoint.class.getName(), -1, true))
        .isNotEqualTo(new Source(AdviceEntryPoint.class.getName(), -1, false));
  }

  @Test
  void propagatesUnexpectedClassLoadingErrors() {
    ReferenceCollector collector = collectBadHelper();
    ClassLoader failingClassLoader =
        new ClassLoader(null) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) {
            throw new AssertionError("unexpected class-loading failure");
          }
        };
    SamePackageAccessValidator validator =
        new SamePackageAccessValidator(
            new HelperClassPredicate(name -> name.equals(BadHelper.class.getName())),
            failingClassLoader);

    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> validator.validate(collector.getReferences()))
        .withMessage("unexpected class-loading failure");
  }

  @Test
  void acceptsHelperToHelperReferences() {
    ReferenceCollector collector =
        collectorForHelpers(HelperCallingHelper.class.getName(), OtherHelper.class.getName());
    collector.collectReferencesFromAdvice(HelperCallingHelperAdvice.class.getName());
    collector.prune();

    assertThatCode(collector::validateSamePackageLibraryAccess).doesNotThrowAnyException();
  }
}
