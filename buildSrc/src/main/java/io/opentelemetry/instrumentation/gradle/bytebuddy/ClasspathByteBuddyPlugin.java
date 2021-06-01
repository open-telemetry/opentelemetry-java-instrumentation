/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.bytebuddy;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

/**
 * Starting from version 1.10.15, ByteBuddy gradle plugin transformations require that plugin
 * classes are given as class instances instead of a class name string. To be able to still use a
 * plugin implementation that is not a buildscript dependency, this reimplements the previous logic
 * by taking a delegate class name and class path as arguments and loading the plugin class from the
 * provided classloader when the plugin is instantiated.
 */
public class ClasspathByteBuddyPlugin implements Plugin {
  private final Plugin delegate;

  /**
   * classPath and className argument resolvers are explicitly added by {@link
   * ClasspathTransformation}, sourceDirectory is automatically resolved as by default any {@link
   * File} argument is resolved to source directory.
   */
  public ClasspathByteBuddyPlugin(
      Iterable<File> classPath, File sourceDirectory, String className) {
    this.delegate = pluginFromClassPath(classPath, sourceDirectory, className);
  }

  @Override
  public DynamicType.Builder<?> apply(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassFileLocator classFileLocator) {

    return delegate.apply(builder, typeDescription, classFileLocator);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public boolean matches(TypeDescription typeDefinitions) {
    return delegate.matches(typeDefinitions);
  }

  private static Plugin pluginFromClassPath(
      Iterable<File> classPath, File sourceDirectory, String className) {
    try {
      ClassLoader classLoader = classLoaderFromClassPath(classPath, sourceDirectory);
      Class<?> clazz = Class.forName(className, false, classLoader);
      return (Plugin) clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create ByteBuddy plugin instance", e);
    }
  }

  private static ClassLoader classLoaderFromClassPath(
      Iterable<File> classPath, File sourceDirectory) {
    List<URL> urls = new ArrayList<>();
    urls.add(fileAsUrl(sourceDirectory));

    for (File file : classPath) {
      urls.add(fileAsUrl(file));
    }

    return new URLClassLoader(urls.toArray(new URL[0]), ByteBuddy.class.getClassLoader());
  }

  private static URL fileAsUrl(File file) {
    try {
      return file.toURI().toURL();
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Cannot resolve " + file + " as URL", e);
    }
  }
}
