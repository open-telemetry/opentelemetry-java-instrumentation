/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.Vector;
import lombok.extern.slf4j.Slf4j;

/**
 * Classloader used to run the core agent.
 *
 * <p>It is built around the concept of a jar inside another jar. This classloader loads the files
 * of the internal jar to load classes and resources.
 */
@Slf4j
public class AgentClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  // Calling java.lang.instrument.Instrumentation#appendToBootstrapClassLoaderSearch
  // adds a jar to the bootstrap class lookup, but not to the resource lookup.
  // As a workaround, we keep a reference to the bootstrap jar
  // to use only for resource lookups.
  private final BootstrapClassLoaderProxy bootstrapProxy;

  private final String internalJarFileName;
  private final AccessControlContext acc;
  private final URL bootstrapJarLocaton;
  /**
   * Construct a new AgentClassLoader
   *
   * @param bootstrapJarLocation Used for resource lookups.
   * @param internalJarFileName File name of the internal jar
   * @param parent Classloader parent. Should null (bootstrap), or the platform classloader for java
   *     9+.
   */
  public AgentClassLoader(
      final URL bootstrapJarLocation, final String internalJarFileName, final ClassLoader parent) {
    super(new URL[] {bootstrapJarLocation}, parent);
    this.internalJarFileName = internalJarFileName;
    this.bootstrapJarLocaton = bootstrapJarLocation;

    // some tests pass null
    bootstrapProxy =
        bootstrapJarLocation == null
            ? new BootstrapClassLoaderProxy(new URL[0])
            : new BootstrapClassLoaderProxy(new URL[] {bootstrapJarLocation});
    this.acc = AccessController.getContext();
  }

  protected Class<?> findClass(final String name) throws ClassNotFoundException {
    Class result;
    try {
      result =
          (Class)
              AccessController.doPrivileged(
                  new PrivilegedExceptionAction<Class<?>>() {
                    public Class<?> run() throws ClassNotFoundException {
                      final String path = name.replace('.', '/').concat(".class");
                      final URL res = AgentClassLoader.this.getResource(path);
                      if (res != null) {
                        try {
                          final ByteArrayOutputStream bout = new ByteArrayOutputStream();
                          final InputStream in = res.openStream();
                          final byte[] buf = new byte[4 * 1024];
                          int read = in.read(buf);
                          while (read != -1) {
                            bout.write(buf, 0, read);
                            read = in.read(buf);
                          }
                          in.close();
                          final byte[] classBytes = bout.toByteArray();
                          final CodeSource cs =
                              new CodeSource(bootstrapJarLocaton, (Certificate[]) null);
                          final PermissionCollection pc = Policy.getPolicy().getPermissions(cs);
                          final ProtectionDomain pd =
                              new ProtectionDomain(cs, pc, AgentClassLoader.this, null);
                          return AgentClassLoader.this.defineClass(
                              name, classBytes, 0, classBytes.length, pd);
                        } catch (IOException var4) {
                          throw new ClassNotFoundException(name, var4);
                        }
                      } else {
                        return null;
                      }
                    }
                  },
                  acc);
    } catch (PrivilegedActionException var4) {
      throw (ClassNotFoundException) var4.getException();
    }

    if (result == null) {
      throw new ClassNotFoundException(name);
    } else {
      return result;
    }
  }

  @Override
  public URL findResource(final String resourceName) {
    String s = internalJarFileName + "/" + resourceName;
    if (resourceName.endsWith(".class")) {
      s += "data";
    }
    return super.findResource(s);
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    URL found = findResource(name);
    Vector<URL> answer = new Vector<>();
    if (found != null) {
      answer.add(found);
    }
    return answer.elements();
  }

  /**
   * @param className binary name of class
   * @return true if this loader has attempted to load the given class
   */
  public boolean hasLoadedClass(final String className) {
    return findLoadedClass(className) != null;
  }

  public BootstrapClassLoaderProxy getBootstrapProxy() {
    return bootstrapProxy;
  }

  /**
   * A stand-in for the bootstrap classloader. Used to look up bootstrap resources and resources
   * appended by instrumentation.
   *
   * <p>This class is thread safe.
   */
  public static final class BootstrapClassLoaderProxy extends URLClassLoader {
    static {
      ClassLoader.registerAsParallelCapable();
    }

    public BootstrapClassLoaderProxy(final URL[] urls) {
      super(urls, null);
    }

    @Override
    public void addURL(final URL url) {
      super.addURL(url);
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
      throw new ClassNotFoundException(name);
    }
  }
}
