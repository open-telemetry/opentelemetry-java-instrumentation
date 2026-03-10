/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.quarkus2plugin;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

@SuppressWarnings("unused")
public class Quarkus2Plugin implements Plugin<Project> {

  @Override
  public void apply(Project project) {
    // we use this plugin with apply false and call its classes directly from the build script
    throw new IllegalStateException("this plugin is not meant to be applied");
  }
}

