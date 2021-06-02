/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.muzzle;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

public abstract class MuzzleDirective {

  private static final Pattern NORMALIZE_NAME_SLUG = Pattern.compile("[^a-zA-Z0-9]+");

  public MuzzleDirective() {
    getName().convention("");
    getSkipVersions().convention(Collections.emptySet());
    getAdditionalDependencies().convention(Collections.emptyList());
    getExcludedDependencies().convention(Collections.emptyList());
    getAssertPass().convention(false);
    getAssertInverse().convention(false);
    getCoreJdk().convention(false);
  }

  public abstract Property<String> getName();

  public abstract Property<String> getGroup();

  public abstract Property<String> getModule();

  public abstract Property<String> getVersions();

  public abstract SetProperty<String> getSkipVersions();

  public abstract ListProperty<String> getAdditionalDependencies();

  public abstract ListProperty<String> getExcludedDependencies();

  public abstract Property<Boolean> getAssertPass();

  public abstract Property<Boolean> getAssertInverse();

  public abstract Property<Boolean> getCoreJdk();

  public void coreJdk() {
    getCoreJdk().set(true);
  }

  /**
   * Adds extra dependencies to the current muzzle test.
   *
   * @param compileString An extra dependency in the gradle canonical form:
   *     '<group_id>:<artifact_id>:<version_id>'.
   */
  public void extraDependency(String compileString) {
    getAdditionalDependencies().add(compileString);
  }

  /**
   * Adds transitive dependencies to exclude from the current muzzle test.
   *
   * @param excludeString A dependency in the gradle canonical form: '<group_id>:<artifact_id>'
   */
  public void excludeDependency(String excludeString) {
    getExcludedDependencies().add(excludeString);
  }

  public void skip(String... version) {
    getSkipVersions().addAll(version);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (getCoreJdk().getOrElse(false)) {
      if (getAssertPass().getOrElse(false)) {
        sb.append("Pass");
      } else {
        sb.append("Fail");
      }
      sb.append("-core-jdk");
    } else {
      if (getAssertPass().getOrElse(false)) {
        sb.append("pass");
      } else {
        sb.append("fail");
      }
      sb.append(getGroup().get())
          .append(':')
          .append(getModule().get())
          .append(':')
          .append(getVersions().get());
    }
    return sb.toString();
  }

  String getNameSlug() {
    return NORMALIZE_NAME_SLUG.matcher(getName().get().trim()).replaceAll("-");
  }

  Set<String> getNormalizedSkipVersions() {
    return getSkipVersions().getOrElse(Collections.emptySet()).stream()
        .map(String::toLowerCase)
        .collect(Collectors.toSet());
  }
}
