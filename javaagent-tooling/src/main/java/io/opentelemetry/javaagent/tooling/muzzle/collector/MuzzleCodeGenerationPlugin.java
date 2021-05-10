/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
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
public class MuzzleCodeGenerationPlugin implements Plugin {

  private static final TypeDescription instrumentationModuleType =
      new TypeDescription.ForLoadedType(InstrumentationModule.class);

  @Override
  public boolean matches(TypeDescription target) {
    if (target.isAbstract()) {
      return false;
    }
    // AutoService annotation is not retained at runtime. Check for InstrumentationModule supertype
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
    return builder.visit(new MuzzleCodeGenerator());
  }

  @Override
  public void close() {}
}
