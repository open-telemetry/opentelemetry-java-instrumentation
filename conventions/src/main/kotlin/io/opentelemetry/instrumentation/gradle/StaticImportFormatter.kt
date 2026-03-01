/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import com.diffplug.spotless.FormatterFunc
import java.io.File
import java.io.Serializable

/**
 * Spotless custom step that rewrites qualified references (e.g. {@code Objects.requireNonNull})
 * into unqualified references and adds the corresponding static import. Runs before
 * googleJavaFormat so that imports are sorted and any newly-unused regular imports are removed.
 */
class StaticImportFormatter : FormatterFunc.NeedsFile, Serializable {

  private data class Rule(
    val className: String,
    val pkg: String,
    val memberPattern: String,
    val lineExcludePattern: String? = null,
    val filePattern: String? = null,
  ) : Serializable

  override fun applyWithFile(input: String, source: File): String {
    val rules = listOf(
      Rule("Objects", "java.util.Objects", "requireNonNull"),
      Rule(
        "ElementMatchers",
        "net.bytebuddy.matcher.ElementMatchers",
        "[a-z][a-zA-Z0-9]*"
      ),
      Rule(
        "AgentElementMatchers",
        "io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers",
        "[a-z][a-zA-Z0-9]*"
      ),
      Rule("TimeUnit", "java.util.concurrent.TimeUnit", "[A-Z][A-Z_0-9]*"),
      Rule(
        "StandardCharsets",
        "java.nio.charset.StandardCharsets",
        "[A-Z][A-Z_0-9]*"
      ),
      Rule(
        "Collections",
        "java.util.Collections",
        "singleton[a-zA-Z0-9]*|empty[a-zA-Z0-9]*"
      ),
      Rule(
        "ArgumentMatchers",
        "org.mockito.ArgumentMatchers",
        "[a-z][a-zA-Z0-9]*"
      ),
      Rule(
        "Mockito",
        "org.mockito.Mockito",
        "mock|mockStatic|spy|when|verify|verifyNoInteractions|verifyNoMoreInteractions|doAnswer|doReturn|doThrow|lenient|never|times|atLeastOnce|withSettings"
      ),
      Rule("Assertions", "org.assertj.core.api.Assertions", "assertThat"),
      Rule(
        "OpenTelemetryAssertions",
        "io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions",
        "[a-z][a-zA-Z0-9]*"
      ),
      Rule(
        "SemconvStability",
        "io.opentelemetry.instrumentation.api.internal.SemconvStability",
        "emit[a-zA-Z0-9]*"
      ),
      Rule(
        "SqlDialect",
        "io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect",
        "DOUBLE_QUOTES_ARE_[A-Z_]+"
      ),
      Rule("Collectors", "java.util.stream.Collectors", "[a-z][a-zA-Z0-9]*"),
      Rule(
        "AttributeKey",
        "io.opentelemetry.api.common.AttributeKey",
        "stringKey|longKey|booleanKey|doubleKey|stringArrayKey|longArrayKey|booleanArrayKey|doubleArrayKey",
        lineExcludePattern = "= AttributeKey.",
        filePattern = "Test\\.java$"
      ),
    )

    var content = input
    val importsToAdd = mutableSetOf<String>()

    for (rule in rules) {
      if (rule.filePattern != null && !Regex(rule.filePattern).containsMatchIn(source.name)) continue
      val regex = Regex("\\b${rule.className}\\.(${rule.memberPattern})\\b")
      val lines = content.lines().toMutableList()
      for (i in lines.indices) {
        if (lines[i].trimStart().startsWith("import ")) continue
        if (rule.lineExcludePattern != null &&
          (maxOf(0, i - 3)..i).joinToString(" ") { j -> lines[j].trim() }.contains(rule.lineExcludePattern)
        ) continue
        for (match in regex.findAll(lines[i])) {
          importsToAdd.add("import static ${rule.pkg}.${match.groupValues[1]};")
        }
        lines[i] = regex.replace(lines[i], "$1")
      }
      content = lines.joinToString("\n")
    }

    // Handle semconv classes: find all imported semconv classes (except SchemaUrls) and convert
    // qualified constant references (e.g. HttpAttributes.HTTP_METHOD) to static imports.
    // Also handles nested value classes (e.g. DbIncubatingAttributes.DbSystemNameIncubatingValues).
    val semconvImportRegex =
      Regex(
        """^import (io\.opentelemetry\.semconv\.(?:[a-z][a-z.]*\.)?([A-Z][a-zA-Z0-9]+)(?:\.([A-Z][a-zA-Z0-9]+))?);"""
      )
    val semconvRules =
      content
        .lines()
        .mapNotNull { line -> semconvImportRegex.find(line.trim()) }
        .filter { it.groupValues[2] != "SchemaUrls" && it.groupValues[3].isEmpty() }
        .map { m ->
          Triple(m.groupValues[2], m.groupValues[1], "[A-Z][A-Z_0-9]*")
        }
    for ((className, pkg, memberPattern) in semconvRules) {
      val regex = Regex("\\b${className}\\.(${memberPattern})\\b")
      val lines = content.lines().toMutableList()
      var inBlockComment = false
      for (i in lines.indices) {
        val trimmed = lines[i].trimStart()
        if (trimmed.startsWith("/*")) inBlockComment = true
        if (inBlockComment) {
          if (trimmed.contains("*/")) inBlockComment = false
          continue
        }
        if (trimmed.startsWith("import ")) continue
        for (match in regex.findAll(lines[i])) {
          importsToAdd.add("import static ${pkg}.${match.groupValues[1]};")
        }
        lines[i] = regex.replace(lines[i], "$1")
      }
      content = lines.joinToString("\n")
    }

    // Handle nested value class references through the outer class, e.g.
    // DbIncubatingAttributes.DbSystemNameIncubatingValues.CASSANDRA when only
    // DbIncubatingAttributes is imported (not the nested class directly).
    // Rewrites to NestedClass.CONSTANT and adds a regular import of the nested class.
    val outerOnlySemconvImports =
      content
        .lines()
        .mapNotNull { line -> semconvImportRegex.find(line.trim()) }
        .filter { it.groupValues[2] != "SchemaUrls" && it.groupValues[3].isEmpty() }
        .map { m -> Pair(m.groupValues[2], m.groupValues[1]) }
    for ((outerClassName, outerPkg) in outerOnlySemconvImports) {
      val regex =
        Regex("\\b${outerClassName}\\.([A-Z][a-zA-Z0-9]*[a-z][a-zA-Z0-9]*)\\.([A-Z][A-Z_0-9]+)\\b")
      val lines = content.lines().toMutableList()
      var inBlockComment = false
      for (i in lines.indices) {
        val trimmed = lines[i].trimStart()
        if (trimmed.startsWith("/*")) inBlockComment = true
        if (inBlockComment) {
          if (trimmed.contains("*/")) inBlockComment = false
          continue
        }
        if (trimmed.startsWith("import ")) continue
        for (match in regex.findAll(lines[i])) {
          importsToAdd.add("import ${outerPkg}.${match.groupValues[1]};")
        }
        lines[i] = regex.replace(lines[i], "$1.$2")
      }
      content = lines.joinToString("\n")
    }

    if (importsToAdd.isNotEmpty()) {
      val lines = content.lines().toMutableList()
      val firstImportIndex = lines.indexOfFirst { it.trimStart().startsWith("import ") }
      if (firstImportIndex >= 0) {
        for ((offset, imp) in importsToAdd.sorted().withIndex()) {
          lines.add(firstImportIndex + offset, imp)
        }
        content = lines.joinToString("\n")
      }
    }

    return content
  }

  companion object {
    private const val serialVersionUID = 1L
  }
}
