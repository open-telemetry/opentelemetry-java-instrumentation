/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import io.opentelemetry.javaagent.tooling.util.ByteArrayUrl;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.StreamDrainer;

/**
 * Provides the bytecode and the original resource URL for loaded and not-yet loaded classes. The
 * implementation is based on {@link net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader}, with
 * the difference that it preserves the original classfile resource URL.
 */
public abstract class ClassCopySource {

  private ClassCopySource() {}

  /**
   * Provides a URL pointing to the specific classfile.
   *
   * @return the URL
   */
  public abstract URL getUrl();

  /**
   * Provides the bytecode of the class. The result is the same as calling {@link URL#openStream()}
   * on {@link #getUrl()} and draining that stream.
   *
   * @return the bytecode of the class.
   */
  public abstract byte[] getBytecode();

  /**
   * Creates a cached copy of this {@link ClassCopySource}. The cached copy eagerly loads the
   * bytecode, so that {@link #getBytecode()} is guaranteed to not cause any IO. This comes at the
   * cost of a higher heap consumption, as the bytecode is kept in memory.
   *
   * @return an ClassFileSource implementing the described caching behaviour.
   */
  public abstract ClassCopySource cached();

  /**
   * Creates a {@link ClassCopySource} for the class with the provided fully qualified name. The
   * .class file for the provided classname must be available as a resource in the provided
   * classloader. The class is guaranteed to not be loaded during this process.
   *
   * @param className the fully qualified name of the class to copy
   * @param classLoader the classloader
   * @return the ClassCopySource which can be used to copy the provided class to other classloaders.
   */
  public static ClassCopySource create(String className, ClassLoader classLoader) {
    if (classLoader == null) {
      throw new IllegalArgumentException(
          "Copying classes from the bootstrap classloader is not supported!");
    }
    String classFileName = className.replace('.', '/') + ".class";
    return new Lazy(classLoader, classFileName);
  }

  /**
   * Same as {@link #create(String, ClassLoader)}, but easier to use for already loaded classes.
   *
   * @param loadedClass the class to copy
   * @return the ClassCopySource which can be used to copy the provided class to other classloaders.
   */
  public static ClassCopySource create(Class<?> loadedClass) {
    return create(loadedClass.getName(), loadedClass.getClassLoader());
  }


  /**
   * Creates a {@link ClassCopySource} for a runtime-generated type.
   * It will also provide an artificially generated {@link URL} pointing to the in-memory bytecode.
   *
   * @param className the name of the class represented by the provided bytecode
   * @param bytecode the bytecode of the class
   * @return the {@link ClassCopySource} referring to this dynamically generated class
   */
  public static ClassCopySource create(String className, byte[] bytecode) {
    return new ForDynamicType(className, bytecode);
  }

  /**
   * Invokes {@link #create(String, byte[])} for the provided dynamic type.
   *
   * @param dynamicType the type to generate the {@link ClassCopySource}
   * @return the {@link ClassCopySource} referring to this dynamically generated type
   */
  public static ClassCopySource create(DynamicType.Unloaded<?> dynamicType) {
    String className = dynamicType.getTypeDescription().getName();
    return new ForDynamicType(className, dynamicType.getBytes());
  }

  private static class Lazy extends ClassCopySource {

    private final ClassLoader classLoader;
    private final String resourceName;

    private Lazy(ClassLoader classLoader, String resourceName) {
      this.classLoader = classLoader;
      this.resourceName = resourceName;
    }

    @Override
    public URL getUrl() {
      URL url = classLoader.getResource(resourceName);
      if (url == null) {
        throw new IllegalStateException(
            "Classfile " + resourceName + " does not exist in the provided classloader!");
      }
      return url;
    }

    @Override
    public byte[] getBytecode() {
      try (InputStream bytecodeStream = getUrl().openStream()) {
        return StreamDrainer.DEFAULT.drain(bytecodeStream);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to read classfile URL", e);
      }
    }

    @Override
    public ClassCopySource cached() {
      return new Cached(this);
    }
  }

  private static class Cached extends ClassCopySource {

    private final URL classFileUrl;

    private final byte[] cachedByteCode;

    private Cached(ClassCopySource.Lazy from) {
      classFileUrl = from.getUrl();
      cachedByteCode = from.getBytecode();
    }

    @Override
    public URL getUrl() {
      return classFileUrl;
    }

    @Override
    public byte[] getBytecode() {
      return cachedByteCode;
    }

    @Override
    public ClassCopySource cached() {
      return this;
    }
  }


  private static class ForDynamicType extends ClassCopySource {

    private final byte[] byteCode;
    private final String className;
    private volatile URL generatedUrl;


    private ForDynamicType(String className, byte[] byteCode) {
      this.byteCode = byteCode;
      this.className = className;
    }

    @Override
    public URL getUrl() {
      if (generatedUrl == null) {
        synchronized (this) {
          if (generatedUrl == null) {
            generatedUrl = ByteArrayUrl.create(className, byteCode);
          }
        }
      }
      return generatedUrl;
    }

    @Override
    public byte[] getBytecode() {
      return byteCode;
    }

    @Override
    public ClassCopySource cached() {
      return this; //this type already holds the bytecode in-memory
    }
  }

}
