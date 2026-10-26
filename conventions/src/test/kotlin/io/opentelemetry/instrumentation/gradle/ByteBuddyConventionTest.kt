/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ByteBuddyConventionTest {
  @TempDir
  lateinit var directory: File

  @Test
  fun `mixed providers fail with an actionable diagnostic`() {
    for (configuration in listOf("compileClasspath", "runtimeClasspath")) {
      fixture("""
      dependencies {
        implementation("net.bytebuddy:byte-buddy:1.18.14")
        implementation("net.bytebuddy:byte-buddy-dep:1.18.14")
      }
      tasks.register("resolve") {
        doLast { configurations.getByName("$configuration").files }
      }
      """)
      assertThat(runner("resolve").buildAndFail().output)
        .contains("contains both byte-buddy (relocated ASM) and byte-buddy-dep (external ASM)")
        .contains("separate Mockito consumers from external-ASM transformations")
    }
  }

  @Test
  fun `explicit external ASM choice does not hide a Mockito dependency`() {
    fixture("""
      useByteBuddy(sourceSets.main.get(), ByteBuddyVariant.EXTERNAL_ASM)
      dependencies {
        implementation("net.bytebuddy:byte-buddy-dep:1.18.14")
        implementation("org.mockito:mockito-inline:4.11.0")
      }
      tasks.register("resolve") {
        doLast { configurations.runtimeClasspath.get().files }
      }
    """)
    assertThat(runner("resolve").buildAndFail().output)
      .contains("Mockito requires standard byte-buddy; separate it from external-ASM transformations")
  }

  @Test
  fun `choices agree on compile and runtime and preserve agent coexistence`() {
    for (variant in listOf("STANDARD", "EXTERNAL_ASM")) {
      fixture("""
        useByteBuddy(sourceSets.main.get(), ByteBuddyVariant.$variant)
        dependencies {
          implementation("net.bytebuddy:byte-buddy:1.18.14")
          implementation("net.bytebuddy:byte-buddy-dep:1.18.14")
          implementation("net.bytebuddy:byte-buddy-agent:1.18.14")
        }
        tasks.register("resolve") {
          doLast {
            val expected = if ("$variant" == "STANDARD") "byte-buddy" else "byte-buddy-dep"
            for (configuration in listOf(configurations.compileClasspath.get(), configurations.runtimeClasspath.get())) {
              val jars = configuration.files
              val providers = jars.filter { jar ->
                ZipFile(jar).use { it.getEntry("net/bytebuddy/ByteBuddy.class") != null }
              }
              check(providers.map { it.name } == listOf(expected + "-1.18.14.jar"))
              check(jars.any { it.name == "byte-buddy-agent-1.18.14.jar" })
            }
          }
        }
      """)
      assertThat(runner("resolve").build().output).contains("BUILD SUCCESSFUL")
    }
  }

  @Test
  fun `bootstrap buildscript uses the same policy without loading the conventions build`() {
    fixture("")
    write("build.gradle.kts", """
      import java.util.zip.ZipFile

      buildscript {
        project.apply(from = "buildscripts/byte-buddy.gradle.kts")
        repositories { mavenCentral() }
        configurations.named("classpath") {
          attributes.attribute(Attribute.of("io.opentelemetry.byte-buddy-variant", String::class.java), "byte-buddy-dep")
        }
        dependencies {
          classpath("net.bytebuddy:byte-buddy:1.18.14")
          classpath("net.bytebuddy:byte-buddy-dep:1.18.14")
        }
      }
      tasks.register("resolve") {
        doLast {
          val providers = project.buildscript.configurations.getByName("classpath").files.filter { jar ->
            ZipFile(jar).use { it.getEntry("net/bytebuddy/ByteBuddy.class") != null }
          }
          check(providers.map { it.name } == listOf("byte-buddy-dep-1.18.14.jar"))
        }
      }
    """)
    assertThat(runner("resolve").build().output).contains("BUILD SUCCESSFUL")
  }

  @Test
  fun `suite opt out preserves default and unitTests inline mocking while external ASM executes`() {
    fixture("""
      apply<MockitoConventionPlugin>()
      dependencies {
        implementation("net.bytebuddy:byte-buddy-dep:1.18.14")
      }
      testing.suites.withType<JvmTestSuite>().configureEach {
        useJUnitJupiter("5.14.4")
        dependencies {
          implementation(platform("org.mockito:mockito-bom:4.11.0"))
          implementation("org.assertj:assertj-core:3.27.7")
          implementation("net.bytebuddy:byte-buddy:1.18.14")
          implementation("net.bytebuddy:byte-buddy-dep:1.18.14")
          implementation("net.bytebuddy:byte-buddy-agent:1.18.14")
        }
        targets.all {
          testTask.configure {
            jvmArgs("-XX:+EnableDynamicAgentLoading")
          }
        }
      }
      testing.suites {
        register<JvmTestSuite>("unitTests") {
        }
        register<JvmTestSuite>("transformations") {
          mockito.enabled.set(false)
          useByteBuddy(sources, ByteBuddyVariant.EXTERNAL_ASM)
        }
      }
    """)
    for (suite in listOf("test", "unitTests")) {
      write("src/$suite/java/example/MockingTest.java", """
        package example;

        import org.junit.jupiter.api.Test;
        import org.mockito.Mockito;
        import org.mockito.MockedStatic;
        import static org.assertj.core.api.Assertions.assertThat;

        class MockingTest {
          static final class FinalType {
            String value() { return "real"; }
            static String staticValue() { return "real"; }
          }

          @Test void mocksFinalAndStaticMethods() {
            FinalType mock = Mockito.mock(FinalType.class);
            Mockito.when(mock.value()).thenReturn("mocked");
            assertThat(mock.value()).isEqualTo("mocked");
            try (MockedStatic<FinalType> mocked = Mockito.mockStatic(FinalType.class)) {
              mocked.when(FinalType::staticValue).thenReturn("inline");
              assertThat(FinalType.staticValue()).isEqualTo("inline");
            }
          }
        }
      """)
    }
    write("src/transformations/java/example/TransformationTest.java", """
      package example;

      import net.bytebuddy.ByteBuddy;
      import net.bytebuddy.description.method.MethodDescription;
      import net.bytebuddy.description.modifier.Visibility;
      import net.bytebuddy.implementation.Implementation;
      import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
      import org.objectweb.asm.MethodVisitor;
      import org.objectweb.asm.Opcodes;
      import org.junit.jupiter.api.Test;
      import static org.assertj.core.api.Assertions.assertThat;

      class TransformationTest {
        @Test void executesExternalAsmImplementationWithoutMockito() throws Exception {
          assertThat(getClass().getClassLoader().getResource("org/mockito/Mockito.class")).isNull();
          Class<?> generated = new ByteBuddy()
              .subclass(Object.class)
              .defineMethod("value", String.class, Visibility.PUBLIC)
              .intercept(new Implementation.Simple(new ByteCodeAppender() {
                @Override public Size apply(MethodVisitor visitor, Implementation.Context context, MethodDescription method) {
                  visitor.visitLdcInsn("external");
                  visitor.visitInsn(Opcodes.ARETURN);
                  return new Size(1, method.getStackSize());
                }
              }))
              .make().load(getClass().getClassLoader()).getLoaded();
          assertThat(generated.getMethod("value").invoke(generated.getConstructor().newInstance())).isEqualTo("external");
        }
      }
    """)
    assertThat(runner("test", "unitTests", "transformations").build().output).contains("BUILD SUCCESSFUL")
    for (suite in listOf("test", "unitTests", "transformations")) {
      val results = directory.resolve("build/test-results/$suite").listFiles()!!.filter { it.extension == "xml" }
      assertThat(results).hasSize(1)
      assertThat(results.single().readText()).contains("tests=\"1\"").contains("failures=\"0\"")
    }
  }

  private fun fixture(body: String) {
    write("settings.gradle.kts", """rootProject.name = "byte-buddy-policy-test" """)
    val script = File("../buildscripts/byte-buddy.gradle.kts")
    write("buildscripts/byte-buddy.gradle.kts", script.readText())
    write("build.gradle.kts", """
      import io.opentelemetry.instrumentation.gradle.ByteBuddyVariant
      import io.opentelemetry.instrumentation.gradle.MockitoConventionPlugin
      import io.opentelemetry.instrumentation.gradle.mockito
      import io.opentelemetry.instrumentation.gradle.useByteBuddy
      import java.util.zip.ZipFile

      plugins {
        `java-library`
        id("otel.byte-buddy-conventions")
      }
      repositories { mavenCentral() }
      $body
    """)
  }

  private fun write(path: String, content: String) {
    directory.resolve(path).apply {
      parentFile.mkdirs()
      writeText(content.trimIndent())
    }
  }

  private fun runner(vararg tasks: String): GradleRunner = GradleRunner.create()
    .withProjectDir(directory)
    .withPluginClasspath()
    .withArguments(*tasks, "--stacktrace", "--console=plain")
}
