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
import java.net.URL;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class ClasspathUtils {

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
    try (InputStream inputStream =
        clazz.getClassLoader().getResourceAsStream(getResourceName(clazz.getName()))) {
      return convertToByteArray(inputStream);
    }
  }

  /**
   * Create a temporary jar on the filesystem with the bytes of the given classes.
   *
   * <p>The jar file will be removed when the jvm exits.
   *
   * @param loader class loader used to load bytes
   * @param resourceNames names of resources to copy into the new jar
   * @return the location of the newly created jar.
   */
  public static URL createJarWithClasses(ClassLoader loader, String... resourceNames)
      throws IOException {
    File tmpJar = File.createTempFile(UUID.randomUUID() + "", ".jar");
    tmpJar.deleteOnExit();

    Manifest manifest = new Manifest();
    JarOutputStream target = new JarOutputStream(new FileOutputStream(tmpJar), manifest);
    for (String resourceName : resourceNames) {
      try (InputStream is = loader.getResourceAsStream(resourceName)) {
        addToJar(resourceName, convertToByteArray(is), target);
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
    File tmpJar = File.createTempFile(UUID.randomUUID() + "", ".jar");
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

  private ClasspathUtils() {}
}
