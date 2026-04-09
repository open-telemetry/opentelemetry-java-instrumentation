/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.generation;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

/**
 * This class is a ByteBuddy build plugin that is responsible for generating actual implementation
 * of some {@link InstrumentationModule} methods. Auto-generated methods have the word "muzzle" in
 * their names.
 *
 * <p>This class is used in the gradle build scripts, referenced by each instrumentation module.
 */
public final class MuzzleCodeGenerationPlugin implements Plugin {

  private static final TypeDescription instrumentationModuleType =
      new TypeDescription.ForLoadedType(InstrumentationModule.class);

  private final URLClassLoader classLoader;

  public MuzzleCodeGenerationPlugin(File[] classpath) throws MalformedURLException {
    URL[] urls = new URL[classpath.length];
    for (int i = 0; i < classpath.length; i++) {
      urls[i] = classpath[i].toURI().toURL();
    }
    this.classLoader = new URLClassLoader(urls, getClass().getClassLoader());
  }

  @Override
  public boolean matches(TypeDescription target) {
    if (target.isAbstract()) {
      return false;
    }
    boolean isInstrumentationModule = false;
    TypeDefinition instrumentation = target.getSuperClass();
    while (instrumentation != null) {
      if (instrumentation.equals(instrumentationModuleType)) {
        isInstrumentationModule = true;
        break;
      }
      instrumentation = instrumentation.getSuperClass();
    }
    return isInstrumentationModule;
  }

  @Override
  public DynamicType.Builder<?> apply(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassFileLocator classFileLocator) {
    return builder.visit(new MuzzleCodeGenerator(classLoader));
  }

  @Override
  public void close() throws IOException {
    classLoader.close();
  }
}
