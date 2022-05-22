/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.util.stream.Collectors

abstract class MuzzleDirective {

  abstract val name: Property<String>
  abstract val group: Property<String>
  abstract val module: Property<String>
  abstract val classifier: Property<String>
  abstract val versions: Property<String>
  abstract val skipVersions: SetProperty<String>
  abstract val additionalDependencies: ListProperty<Any>
  abstract val excludedDependencies: ListProperty<String>
  abstract val excludedInstrumentationNames: SetProperty<String>
  abstract val assertPass: Property<Boolean>
  abstract val assertInverse: Property<Boolean>
  internal abstract val coreJdk: Property<Boolean> // use coreJdk() function below to enable

  init {
    name.convention("")
    classifier.convention("")
    skipVersions.convention(emptySet())
    additionalDependencies.convention(listOf())
    excludedDependencies.convention(listOf())
    excludedInstrumentationNames.convention(listOf())
    assertPass.convention(false)
    assertInverse.convention(false)
    coreJdk.convention(false)
  }

  fun coreJdk() {
    coreJdk.set(true)
  }

  /**
   * Adds extra dependencies to the current muzzle test.
   *
   * @param dependencyNotation An extra dependency in the gradle canonical form:
   * '<group_id>:<artifact_id>:<version_id>' or a project dependency project(':some-project').
   */
  fun extraDependency(dependencyNotation: Any) {
    additionalDependencies.add(dependencyNotation)
  }

  /**
   * Adds transitive dependencies to exclude from the current muzzle test.
   *
   * @param excludeString A dependency in the gradle canonical form: '<group_id>:<artifact_id>'
   */
  fun excludeDependency(excludeString: String?) {
    excludedDependencies.add(excludeString!!)
  }

  /**
   * Excludes an instrumentation module from the current muzzle test.
   *
   * @param excludeString An instrumentation module class name to exclude
   */
  fun excludeInstrumentationName(excludeString: String) {
    excludedInstrumentationNames.add(excludeString)
  }

  fun skip(vararg version: String?) {
    skipVersions.addAll(*version)
  }

  internal val nameSlug: String
    get() = NORMALIZE_NAME_SLUG.replace(name.get().trim(), "-")

  internal val normalizedSkipVersions: Set<String>
    get() = skipVersions.getOrElse(setOf()).stream()
      .map(String::toLowerCase)
      .collect(Collectors.toSet())

  override fun toString(): String {
    val sb = StringBuilder()
    if (coreJdk.getOrElse(false)) {
      if (assertPass.getOrElse(false)) {
        sb.append("Pass")
      } else {
        sb.append("Fail")
      }
      sb.append("-core-jdk")
    } else {
      if (assertPass.getOrElse(false)) {
        sb.append("pass")
      } else {
        sb.append("fail")
      }
      sb.append(group.get())
        .append(':')
        .append(module.get())
        .append(':')
        .append(versions.get())
      if (classifier.isPresent) {
        sb.append(':').append(classifier.get())
      }
    }
    return sb.toString()
  }

  companion object {
    private val NORMALIZE_NAME_SLUG = Regex("[^a-zA-Z0-9]+")
  }
}
