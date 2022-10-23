/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.generation

import net.bytebuddy.ByteBuddy
import net.bytebuddy.build.Plugin
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.dynamic.DynamicType
import java.io.File
import java.net.URL
import java.net.URLClassLoader

/**
 * Starting from version 1.10.15, ByteBuddy gradle plugin transformations require that plugin
 * classes are given as class instances instead of a class name string. To be able to still use a
 * plugin implementation that is not a buildscript dependency, this reimplements the previous logic
 * by taking a delegate class name and class path as arguments and loading the plugin class from the
 * provided class loader when the plugin is instantiated.
 */
class ClasspathByteBuddyPlugin(
  classPath: Iterable<File>,
  sourceDirectory: File,
  className: String
) : Plugin {
  private val delegate = pluginFromClassPath2(classPath, sourceDirectory, className)

  override fun apply(
    builder: DynamicType.Builder<*>,
    typeDescription: TypeDescription,
    classFileLocator: ClassFileLocator
  ): DynamicType.Builder<*> {
    return delegate.apply(builder, typeDescription, classFileLocator)
  }

  override fun close() {
    delegate.close()
  }

  override fun matches(typeDefinitions: TypeDescription): Boolean {
    return delegate.matches(typeDefinitions)
  }

  companion object {
    private fun pluginFromClassPath2(
      classPath: Iterable<File>,
      sourceDirectory: File,
      className: String
    ): Plugin {
      val classLoader = classLoaderFromClassPath(classPath, sourceDirectory)
      try {
        val clazz = Class.forName(className, false, classLoader)
        return clazz.getDeclaredConstructor(URLClassLoader::class.java).newInstance(classLoader) as Plugin
      } catch (e: Exception) {
        throw IllegalStateException("Failed to create ByteBuddy plugin instance", e)
      }
    }

    private fun classLoaderFromClassPath(
      classPath: Iterable<File>,
      sourceDirectory: File
    ): URLClassLoader {
      val urls = mutableListOf<URL>()
      urls.add(fileAsUrl(sourceDirectory))
      for (file in classPath) {
        urls.add(fileAsUrl(file))
      }
      return URLClassLoader(urls.toTypedArray(), ByteBuddy::class.java.classLoader)
    }

    private fun fileAsUrl(file: File): URL {
      return file.toURI().toURL()
    }
  }
}
