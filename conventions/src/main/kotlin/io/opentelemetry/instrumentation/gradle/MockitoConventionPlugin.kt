/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.provider.Property
import org.gradle.testing.base.TestingExtension

abstract class MockitoExtension {
  abstract val enabled: Property<Boolean>

  init {
    enabled.convention(true)
  }
}

val JvmTestSuite.mockito: MockitoExtension
  get() = (this as ExtensionAware).extensions.getByType(MockitoExtension::class.java)

class MockitoConventionPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.extensions.getByType(TestingExtension::class.java).suites.withType(JvmTestSuite::class.java).configureEach {
      val mockito = (this as ExtensionAware).extensions.create("mockito", MockitoExtension::class.java)
      val mockitoDependencies = listOf("mockito-core", "mockito-inline", "mockito-junit-jupiter").map {
        project.dependencies.create("org.mockito:$it")
      }
      for (name in listOf(sources.compileClasspathConfigurationName, sources.runtimeClasspathConfigurationName)) {
        project.configurations.named(name) {
          attributes.attributeProvider(
            Attribute.of("io.opentelemetry.byte-buddy-variant", String::class.java),
            mockito.enabled.map { if (it) "byte-buddy" else "unspecified" }
          )
        }
      }
      dependencies {
        implementation.bundle(mockito.enabled.map { enabled ->
          if (enabled) {
            mockitoDependencies
          } else {
            emptyList()
          }
        })
      }
    }
  }
}
