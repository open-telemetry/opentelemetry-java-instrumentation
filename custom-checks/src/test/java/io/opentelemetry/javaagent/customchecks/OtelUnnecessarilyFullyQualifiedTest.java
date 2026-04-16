/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2020 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.customchecks;

import com.google.errorprone.BugCheckerRefactoringTestHelper;
import com.google.errorprone.CompilationTestHelper;
import org.junit.jupiter.api.Test;

/** Tests for {@link OtelUnnecessarilyFullyQualified}. */
class OtelUnnecessarilyFullyQualifiedTest {
  private final BugCheckerRefactoringTestHelper helper =
      BugCheckerRefactoringTestHelper.newInstance(
          OtelUnnecessarilyFullyQualified.class, getClass());

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(OtelUnnecessarilyFullyQualified.class, getClass());

  @Test
  void singleUse() {
    helper
        .addInputLines(
            "Test.java",
            """
            interface Test {
              java.util.List foo();

              java.util.List bar();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.List;

            interface Test {
              List foo();

              List bar();
            }
            """)
        .doTest();
  }

  @Test
  void wouldBeAmbiguous() {
    helper
        .addInputLines(
            "List.java", //
            "class List {}")
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            interface Test {
              java.util.List foo();
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void refersToMultipleTypes() {
    helper
        .addInputLines(
            "List.java",
            """
            package a;

            public class List {}
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            package b;

            interface Test {
              java.util.List foo();

              a.List bar();
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void refersToMultipleTypes_dependingOnLocation() {
    helper
        .addInputLines(
            "Outer.java",
            """
            package a;

            public class Outer {
              public class List {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            package b;

            import a.Outer;

            interface Test {
              java.util.List foo();

              public abstract class Inner extends Outer {
                abstract List bar();
              }
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void inconsistentImportUsage() {
    helper
        .addInputLines(
            "Test.java",
            """
            import java.util.List;

            public class Test {
              public java.util.List<?> foo(List<?> list) {
                return list;
              }
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import java.util.List;

            public class Test {
              public List<?> foo(List<?> list) {
                return list;
              }
            }
            """)
        .doTest();
  }

  @Test
  void clashesWithTypeInSuperType() {
    helper
        .addInputLines(
            "A.java",
            """
            package a;

            public interface A {
              public static class List {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            package b;

            import a.A;

            class Test implements A {
              java.util.List foo() {
                return null;
              }
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void builder() {
    helper
        .addInputLines(
            "Foo.java",
            """
            package a;

            public class Foo {
              public static final class Builder {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            package b;

            interface Test {
              a.Foo foo();

              a.Foo.Builder fooBuilder();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            package b;

            import a.Foo;

            interface Test {
              Foo foo();

              Foo.Builder fooBuilder();
            }
            """)
        .doTest();
  }

  @Test
  void exemptedNames() {
    helper
        .addInputLines(
            "Annotation.java",
            """
            package pkg;

            public class Annotation {}
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            interface Test {
              pkg.Annotation foo();
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void innerClass() {
    helper
        .addInputLines(
            "A.java",
            """
            package test;

            public class A {
              class B {}

              void test(A a) {
                a.new B() {};
              }
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void packageInfo() {
    compilationHelper
        .addSourceLines(
            "a/A.java",
            """
            package a;

            public @interface A {}
            """)
        .addSourceLines(
            "b/package-info.java",
            """
            @a.A
            package b;
            """)
        .doTest();
  }

  @Test
  void staticNestedClass() {
    helper
        .addInputLines(
            "test/EnclosingType.java",
            """
            package test;

            public final class EnclosingType {
              public static final class StaticNestedClass {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            interface Test {
              test.EnclosingType.StaticNestedClass method();
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import test.EnclosingType.StaticNestedClass;

            interface Test {
              StaticNestedClass method();
            }
            """)
        .doTest();
  }

  @Test
  void exemptedEnclosingTypes() {
    helper
        .setArgs("-XepOpt:BadImport:BadEnclosingTypes=org.immutables.value.Value")
        .addInputLines(
            "org/immutables/value/Value.java",
            """
            package org.immutables.value;

            public @interface Value {
              @interface Immutable {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            import org.immutables.value.Value.Immutable;

            class Test {
              @org.immutables.value.Value.Immutable
              abstract class AbstractType {}
            }
            """)
        .addOutputLines(
            "Test.java",
            """
            import org.immutables.value.Value;
            import org.immutables.value.Value.Immutable;

            class Test {
              @Value.Immutable
              abstract class AbstractType {}
            }
            """)
        .doTest();
  }

  @Test
  void exemptedEnclosingTypes_importWouldBeAmbiguous() {
    helper
        .setArgs("-XepOpt:BadImport:BadEnclosingTypes=org.immutables.value.Value")
        .addInputLines(
            "org/immutables/value/Value.java",
            """
            package org.immutables.value;

            public @interface Value {
              @interface Immutable {}
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "annotation/Value.java",
            """
            package annotation;

            public @interface Value {
              String value();
            }
            """)
        .expectUnchanged()
        .addInputLines(
            "Test.java",
            """
            import annotation.Value;

            final class Test {
              Test(@Value("test") String value) {}

              @org.immutables.value.Value.Immutable
              abstract class AbstractType {}
            }
            """)
        .expectUnchanged()
        .doTest();
  }

  @Test
  void unbatchedFindings() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              // BUG: Diagnostic contains:
              java.util.List foo();

              // BUG: Diagnostic contains:
              java.util.List bar();
            }
            """)
        .doTest();
  }

  @Test
  void batchedFindings() {
    compilationHelper
        .addSourceLines(
            "Test.java",
            """
            interface Test {
              // BUG: Diagnostic contains:
              java.util.List foo();

              java.util.List bar();
            }
            """)
        .setArgs("-XepOpt:OtelUnnecessarilyFullyQualified:BatchFindings=true")
        .doTest();
  }

  @Test
  void lambdaParameter() {
    compilationHelper
        .addSourceLines(
            "Lib.java",
            """
            import java.util.List;
            import java.util.stream.Stream;

            interface Lib {
              Stream<List<String>> f();
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              void f(Lib l) {
                l.f().map(x -> x);
              }

              void g(Lib l) {
                l.f().map(x -> x);
              }
            }
            """)
        .setArgs("-XepOpt:OtelUnnecessarilyFullyQualified:BatchFindings=true")
        .doTest();
  }

  @Test
  void applicationClassesNotFlagged() {
    // application.io.opentelemetry.* classes are shaded copies that must use fully qualified
    // names to avoid conflicts with the agent's own copy of the classes
    compilationHelper
        .addSourceLines(
            "application/io/opentelemetry/api/trace/SpanKind.java",
            """
            package application.io.opentelemetry.api.trace;

            public enum SpanKind {
              INTERNAL,
              SERVER,
              CLIENT
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              application.io.opentelemetry.api.trace.SpanKind method() {
                return application.io.opentelemetry.api.trace.SpanKind.INTERNAL;
              }
            }
            """)
        .doTest();
  }

  @Test
  void applicationAnnotationsNotFlagged() {
    compilationHelper
        .addSourceLines(
            "application/io/opentelemetry/extension/annotations/WithSpan.java",
            """
            package application.io.opentelemetry.extension.annotations;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            public @interface WithSpan {
              String value() default "";
            }
            """)
        .addSourceLines(
            "Test.java",
            """
            class Test {
              application.io.opentelemetry.extension.annotations.WithSpan getAnnotation() {
                return null;
              }
            }
            """)
        .doTest();
  }
}
