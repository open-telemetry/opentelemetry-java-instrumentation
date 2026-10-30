/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.internal;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

/** Owns the instrumentation class loader for one module's Muzzle assertions. */
public abstract class MuzzleClassLoaderService
    implements BuildService<MuzzleClassLoaderService.Parameters>, AutoCloseable {
  private URLClassLoader classLoader;

  public synchronized ClassLoader getClassLoader() throws MalformedURLException {
    if (classLoader == null) {
      URL[] urls = {
        getParameters().getModuleJar().get().getAsFile().toURI().toURL(),
        getParameters().getToolingJar().get().getAsFile().toURI().toURL()
      };
      classLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
    }
    return classLoader;
  }

  @Override
  public synchronized void close() throws IOException {
    if (classLoader != null) {
      try {
        classLoader.close();
      } finally {
        classLoader = null;
      }
    }
  }

  public interface Parameters extends BuildServiceParameters {
    RegularFileProperty getModuleJar();

    RegularFileProperty getToolingJar();
  }
}
