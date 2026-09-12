/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle.samepackage;

/**
 * Test fixtures for {@code SamePackageAccessValidator}. All classes below live in the same package
 * on purpose: the validator flags helper -&gt; library references made across classes that share a
 * package name, since that access pattern is not guaranteed to work at runtime.
 */
@SuppressWarnings({"unused", "ReturnValueIgnored"})
public class SamePackageAccessTestClasses {

  // ----- "library" classes (not registered as instrumentation helper classes in tests) -----

  public static class LibraryClass {
    public int publicField;
    protected int protectedField;
    int packagePrivateField;

    public LibraryClass() {}

    LibraryClass(int unused) {}

    public void publicMethod() {}

    protected void protectedMethod() {}

    void packagePrivateMethod() {}
  }

  static class PackagePrivateLibraryClass {
    public void someMethod() {}
  }

  public static class LibrarySuperClass {
    protected int inheritedProtectedField;

    protected void inheritedProtectedMethod() {}
  }

  public static class LibrarySubClass extends LibrarySuperClass {}

  // ----- instrumentation "advice" entry points: these simulate the actual bytecode advice
  // classes, which start the reference-collection traversal but whose own method bodies are
  // inlined into the instrumented class at runtime -----

  public static class BadHelperAdvice {
    void onEnter() {
      new BadHelper();
    }
  }

  public static class GoodHelperAdvice {
    void onEnter() {
      new GoodHelper().usePublicMethod(new LibraryClass());
    }
  }

  public static class HelperCallingHelperAdvice {
    void onEnter() {
      new HelperCallingHelper().callOtherHelper();
    }
  }

  // ----- instrumentation helper classes (registered as helper classes in tests) -----

  public static class BadHelper {
    void usePackagePrivateClass() {
      new PackagePrivateLibraryClass().someMethod();
    }

    void usePackagePrivateConstructor() {
      new LibraryClass(1);
    }

    void usePackagePrivateMethod(LibraryClass library) {
      library.packagePrivateMethod();
    }

    void usePackagePrivateField(LibraryClass library) {
      int unused = library.packagePrivateField;
    }

    void useProtectedMethod(LibraryClass library) {
      library.protectedMethod();
    }

    void useProtectedField(LibraryClass library) {
      int unused = library.protectedField;
    }

    void useInheritedProtectedMethod(LibrarySubClass library) {
      library.inheritedProtectedMethod();
    }

    void useInheritedProtectedField(LibrarySubClass library) {
      int unused = library.inheritedProtectedField;
    }

    void multipleViolations(LibraryClass library) {
      new PackagePrivateLibraryClass().someMethod();
      library.packagePrivateMethod();
      int unused = library.packagePrivateField;
    }
  }

  public static class GoodHelper {
    void usePublicMethod(LibraryClass library) {
      library.publicMethod();
    }

    void usePublicField(LibraryClass library) {
      int unused = library.publicField;
    }

    void createLibraryClass() {
      new LibraryClass();
    }
  }

  // simulates an advice class: its body is inlined into the instrumented class at runtime, so
  // same-package access made directly from here is not subject to this check
  public static class AdviceEntryPoint {
    void onEnter() {
      new PackagePrivateLibraryClass().someMethod();
      LibraryClass library = new LibraryClass(1);
      library.packagePrivateMethod();
      int unused = library.packagePrivateField;
    }
  }

  public static class HelperCallingHelper {
    void callOtherHelper() {
      new OtherHelper().helperPackagePrivateMethod();
    }
  }

  public static class OtherHelper {
    void helperPackagePrivateMethod() {}
  }

  private SamePackageAccessTestClasses() {}
}
