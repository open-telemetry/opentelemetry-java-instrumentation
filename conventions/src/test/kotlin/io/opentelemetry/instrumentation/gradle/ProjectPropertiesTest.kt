/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ProjectPropertiesTest {
  private val root = ProjectBuilder.builder().withName("root").build()
  private val parent = ProjectBuilder.builder().withName("parent").withParent(root).build()
  private val child = ProjectBuilder.builder().withName("child").withParent(parent).build()

  @Test
  fun absentProperty() {
    assertThat(child.findInheritedExtraProperty("otel.stable")).isNull()
    assertThat(child.findInheritedExtraProperty("mavenGroupId")).isNull()
  }

  @Test
  fun inheritsFromNearestParent() {
    root.extensions.extraProperties.set("otel.stable", "false")
    parent.extensions.extraProperties.set("otel.stable", "true")
    root.extensions.extraProperties.set("mavenGroupId", "io.opentelemetry.javaagent")

    assertThat(child.findInheritedExtraProperty("otel.stable")).isEqualTo("true")
    assertThat(child.findInheritedExtraProperty("mavenGroupId")).isEqualTo("io.opentelemetry.javaagent")
  }

  @Test
  fun localPropertyTakesPrecedence() {
    parent.extensions.extraProperties.set("otel.stable", "true")
    child.extensions.extraProperties.set("otel.stable", "false")
    parent.extensions.extraProperties.set("mavenGroupId", "io.opentelemetry.instrumentation")
    child.extensions.extraProperties.set("mavenGroupId", "io.opentelemetry.javaagent.instrumentation")

    assertThat(child.findInheritedExtraProperty("otel.stable")).isEqualTo("false")
    assertThat(child.findInheritedExtraProperty("mavenGroupId")).isEqualTo("io.opentelemetry.javaagent.instrumentation")
  }

  @Test
  fun nullPropertyShadowsParent() {
    parent.extensions.extraProperties.set("otel.stable", "true")
    child.extensions.extraProperties.set("otel.stable", null)

    assertThat(child.findInheritedExtraProperty("otel.stable")).isNull()
  }

  @Test
  fun readsCurrentValueWithoutCoercion() {
    parent.extensions.extraProperties.set("otel.stable", "true")
    assertThat(child.findInheritedExtraProperty("otel.stable")).isEqualTo("true")

    parent.extensions.extraProperties.set("otel.stable", true)
    assertThat(child.findInheritedExtraProperty("otel.stable")).isEqualTo(true)
  }
}
