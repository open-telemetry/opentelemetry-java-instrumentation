/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import japicmp.cmp.JApiCmpArchive
import japicmp.cmp.JarArchiveComparator
import japicmp.cmp.JarArchiveComparatorOptions
import japicmp.model.AccessModifier
import japicmp.model.JApiClass
import japicmp.model.JApiChangeStatus
import japicmp.model.JApiCompatibilityChangeType
import japicmp.model.JApiMethod
import japicmp.model.JApiSemanticVersionLevel
import me.champeau.gradle.japicmp.report.Severity
import me.champeau.gradle.japicmp.report.Violation
import me.champeau.gradle.japicmp.report.ViolationsGenerator
import me.champeau.gradle.japicmp.report.stdrules.BinaryIncompatibleRule
import me.champeau.gradle.japicmp.report.stdrules.RecordSeenMembersSetup
import me.champeau.gradle.japicmp.report.stdrules.UnchangedMemberRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

class SourceIncompatibleRuleTest {
  @TempDir lateinit var tempDir: Path

  @Test
  fun `detects binary and generic-only source breaks while allowing new generic parameters`() {
    val comparison =
      compare(
        mapOf(
          "sample/GenericClass.java" to "package sample; public class GenericClass<T> {}",
          "sample/GenericMethod.java" to
            "package sample; public class GenericMethod { public java.util.List<String> value() { return null; } }",
          "sample/AbstractBase.java" to "package sample; public abstract class AbstractBase {}",
          "sample/BinaryApi.java" to "package sample; public class BinaryApi { public void removed() {} }",
          "sample/AddedParameter.java" to "package sample; public class AddedParameter {}",
        ),
        mapOf(
          "sample/GenericClass.java" to "package sample; public class GenericClass {}",
          "sample/GenericMethod.java" to
            "package sample; public class GenericMethod { public java.util.List<Integer> value() { return null; } }",
          "sample/AbstractBase.java" to
            "package sample; public abstract class AbstractBase implements Runnable {}",
          "sample/BinaryApi.java" to "package sample; public class BinaryApi {}",
          "sample/AddedParameter.java" to "package sample; public class AddedParameter<T> {}",
        ),
      )
    val errors = comparison.violations.filter { it.severity == Severity.error }

    assertThat(errors.mapNotNull { (it.member as? JApiClass)?.getFullyQualifiedName() })
      .contains("sample.GenericClass", "sample.AbstractBase")
      .doesNotContain("sample.AddedParameter")
    assertThat(
      errors.mapNotNull {
        (it.member as? JApiMethod)?.getjApiClass()?.getFullyQualifiedName()
      }
    )
      .contains("sample.GenericMethod", "sample.BinaryApi")
    assertThat(
      comparison.classes
        .flatMap { it.methods }
        .single { it.name == "value" }
        .changeStatus
    )
      .isEqualTo(JApiChangeStatus.UNCHANGED)
  }

  @Test
  fun `allows added default methods abstract-to-default changes and inherited defaults`() {
    val comparison =
      compare(
        mapOf(
          "sample/AddedDefault.java" to "package sample; public interface AddedDefault {}",
          "sample/ConvertedDefault.java" to
            "package sample; public interface ConvertedDefault { void invoke(); }",
          "sample/Root.java" to "package sample; public interface Root { void invoke(); }",
          "sample/Child.java" to "package sample; public interface Child extends Root {}",
          "sample/Base.java" to "package sample; public abstract class Base implements Child {}",
        ),
        mapOf(
          "sample/AddedDefault.java" to
            "package sample; public interface AddedDefault { default void invoke() {} }",
          "sample/ConvertedDefault.java" to
            "package sample; public interface ConvertedDefault { default void invoke() {} }",
          "sample/Root.java" to "package sample; public interface Root { void invoke(); }",
          "sample/Child.java" to
            "package sample; public interface Child extends Root { @Override default void invoke() {} }",
          "sample/Base.java" to "package sample; public abstract class Base implements Child {}",
        ),
      )

    assertThat(comparison.violations.filter { it.severity == Severity.error }).isEmpty()
  }

  private fun compare(
    oldSources: Map<String, String>,
    newSources: Map<String, String>,
  ): Comparison {
    val oldJar = createJar("old", oldSources)
    val newJar = createJar("new", newSources)
    val options =
      JarArchiveComparatorOptions().apply {
        accessModifier = AccessModifier.PUBLIC
        oldClassPath = listOf(oldJar.absolutePath)
        newClassPath = listOf(newJar.absolutePath)
        listOf(
            JApiCompatibilityChangeType.METHOD_NEW_DEFAULT,
            JApiCompatibilityChangeType.METHOD_ABSTRACT_NOW_DEFAULT,
          )
          .forEach { change ->
            addOverrideCompatibilityChange(
              JarArchiveComparatorOptions.OverrideCompatibilityChange(
                change,
                true,
                true,
                JApiSemanticVersionLevel.MINOR,
              )
            )
          }
      }
    val classes =
      try {
        JarArchiveComparator(options)
          .compare(JApiCmpArchive(oldJar, "baseline"), JApiCmpArchive(newJar, "current"))
      } finally {
        JApiCompatibilityChangeType.values().forEach { it.resetOverrides() }
      }
    val generator =
      ViolationsGenerator(emptyList(), emptyList()).apply {
        addRule(RecordSeenMembersSetup())
        addRule(japicmp.model.JApiChangeStatus.UNCHANGED, SourceIncompatibleRule())
        addRule(japicmp.model.JApiChangeStatus.UNCHANGED, UnchangedMemberRule())
        addRule(BinaryIncompatibleRule())
        addRule(SourceIncompatibleRule())
      }
    return Comparison(classes, generator.toViolations(classes).values.flatten())
  }

  private data class Comparison(val classes: List<JApiClass>, val violations: List<Violation>)

  private fun createJar(name: String, sources: Map<String, String>): java.io.File {
    val sourceDir = Files.createDirectory(tempDir.resolve("${name}-sources"))
    val classesDir = Files.createDirectory(tempDir.resolve("${name}-classes"))
    val sourceFiles =
      sources.map { (relativePath, source) ->
        val file = sourceDir.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, source)
        file
      }
    val arguments =
      listOf("-d", classesDir.toString()) + sourceFiles.map { it.toString() }
    val result = ToolProvider.getSystemJavaCompiler().run(null, null, null, *arguments.toTypedArray())
    assertThat(result).isZero()

    val jar = tempDir.resolve("$name.jar")
    JarOutputStream(Files.newOutputStream(jar)).use { output ->
      Files.walk(classesDir).use { paths ->
        paths.filter { Files.isRegularFile(it) }.forEach { classFile ->
          val entryName = classesDir.relativize(classFile).toString().replace('\\', '/')
          output.putNextEntry(JarEntry(entryName))
          Files.copy(classFile, output)
          output.closeEntry()
        }
      }
    }
    return jar.toFile()
  }
}
