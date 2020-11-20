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

  public ClasspathByteBuddyPlugin(List<Iterable<File>> classPath, String className) {
    this.delegate = pluginFromClassPath(classPath, className);
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

  private static Plugin pluginFromClassPath(List<Iterable<File>> classPath, String className) {
    try {
      ClassLoader classLoader = classLoaderFromClassPath(classPath);
      Class<?> clazz = Class.forName(className, false, classLoader);
      return (Plugin) clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to create ByteBuddy plugin instance", e);
    }
  }

  private static ClassLoader classLoaderFromClassPath(List<Iterable<File>> classPath) {
    List<URL> urls = new ArrayList<>();

    for (Iterable<File> fileList : classPath) {
      for (File file : fileList) {
        try {
          URL url = file.toURI().toURL();
          urls.add(url);
        } catch (MalformedURLException e) {
          throw new RuntimeException("Cannot resolve " + file + " as URL", e);
        }
      }
    }

    return new URLClassLoader(urls.toArray(new URL[0]), ByteBuddy.class.getClassLoader());
  }
}
