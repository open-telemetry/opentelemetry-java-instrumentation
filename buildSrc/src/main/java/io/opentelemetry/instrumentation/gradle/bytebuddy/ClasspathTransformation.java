/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.bytebuddy;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import net.bytebuddy.build.Plugin.Factory.UsingReflection.ArgumentResolver;
import net.bytebuddy.build.gradle.Transformation;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;

/**
 * Special implementation of {@link Transformation} is required as classpath argument must be
 * exposed to Gradle via {@link Classpath} annotation, which cannot be done if it is returned by
 * {@link Transformation#getArguments()}.
 */
public class ClasspathTransformation extends Transformation {
  private final Iterable<File> classpath;
  private final String pluginClassName;

  public ClasspathTransformation(Iterable<File> classpath, String pluginClassName) {
    this.classpath = classpath;
    this.pluginClassName = pluginClassName;
  }

  @Classpath
  public Iterable<? extends File> getClasspath() {
    return classpath;
  }

  @Input
  public String getPluginClassName() {
    return pluginClassName;
  }

  protected List<ArgumentResolver> makeArgumentResolvers() {
    return Arrays.asList(
        new ArgumentResolver.ForIndex(0, classpath),
        new ArgumentResolver.ForIndex(2, pluginClassName));
  }
}
