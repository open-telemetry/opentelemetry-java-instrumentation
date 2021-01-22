/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class ClasspathUtils {

  public static byte[] convertToByteArray(InputStream resource) throws IOException {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    int bytesRead;
    byte[] data = new byte[1024];
    while ((bytesRead = resource.read(data, 0, data.length)) != -1) {
      buffer.write(data, 0, bytesRead);
    }
    buffer.flush();
    return buffer.toByteArray();
  }

  public static byte[] convertToByteArray(Class<?> clazz) throws IOException {
    InputStream inputStream = null;
    try {
      inputStream = clazz.getClassLoader().getResourceAsStream(getResourceName(clazz.getName()));
      return convertToByteArray(inputStream);
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param loader classloader used to load bytes
   * @param resourceNames names of resources to copy into the new jar
   * @return the location of the newly created jar.
   */
  public static URL createJarWithClasses(ClassLoader loader, String... resourceNames)
      throws IOException {
    File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "", ".jar");
    tmpJar.deleteOnExit();

    Manifest manifest = new Manifest();
    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (String resourceName : resourceNames) {
      InputStream is = null;
      try {
        is = loader.getResourceAsStream(resourceName);
        addToJar(resourceName, convertToByteArray(is), target);
      } finally {
        if (null != is) {
          is.close();
        }
      }
    }
    target.close();

    return tmpJar.toURI().toURL();
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param classes classes to package into the jar.
   * @return the location of the newly created jar.
   */
  public static URL createJarWithClasses(Class<?>... classes) throws IOException {
    File tmpJar = File.createTempFile(UUID.randomUUID().toString() + "", ".jar");
    tmpJar.deleteOnExit();

    Manifest manifest = new Manifest();
    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (Class<?> clazz : classes) {
      addToJar(getResourceName(clazz.getName()), convertToByteArray(clazz), target);
    }
    target.close();

    return tmpJar.toURI().toURL();
  }

  private static void addToJar(String resourceName, byte[] bytes, JarOutputStream jarOutputStream)
      throws IOException {
    JarEntry entry = new JarEntry(resourceName);
    jarOutputStream.putNextEntry(entry);
    jarOutputStream.write(bytes, 0, bytes.length);
    jarOutputStream.closeEntry();
  }

  /**
   * Parse JVM classpath and return ClassLoader containing all classpath entries. Inspired by Guava.
   */
  public static ClassLoader buildJavaClassPathClassLoader() {
    List<URL> urls = new ArrayList<>();
    for (String entry : getClasspath()) {
      try {
        try {
          urls.add(new File(entry).toURI().toURL());
        } catch (SecurityException e) { // File.toURI checks to see if the file is a directory
          urls.add(new URL("file", null, new File(entry).getAbsolutePath()));
        }
      } catch (MalformedURLException e) {
        System.err.printf(
            "Error injecting bootstrap jar: Malformed classpath entry: %s. %s%n", entry, e);
      }
    }
    return new URLClassLoader(urls.toArray(new URL[0]), null);
  }

  private static String[] getClasspath() {
    return System.getProperty("java.class.path").split(System.getProperty("path.separator"));
  }

  // Moved this to a java class because groovy was adding a hard ref to classLoader
  public static boolean isClassLoaded(String className, ClassLoader classLoader) {
    try {
      Method findLoadedClassMethod =
          ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
      try {
        findLoadedClassMethod.setAccessible(true);
        Class<?> loadedClass = (Class<?>) findLoadedClassMethod.invoke(classLoader, className);
        return null != loadedClass && loadedClass.getClassLoader() == classLoader;
      } catch (Exception e) {
        throw new IllegalStateException(e);
      } finally {
        findLoadedClassMethod.setAccessible(false);
      }
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  /** com.foo.Bar to com/foo/Bar.class */
  private static String getResourceName(String className) {
    if (!className.endsWith(".class")) {
      return className.replace('.', '/') + ".class";
    } else {
      return className;
    }
  }
}
