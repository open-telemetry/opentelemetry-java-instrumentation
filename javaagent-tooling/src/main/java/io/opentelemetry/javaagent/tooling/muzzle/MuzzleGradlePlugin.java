/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.auto.api.WeakMap;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import java.util.Collections;
import java.util.WeakHashMap;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.description.type.TypeDefinition;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;

/** Bytebuddy gradle plugin which creates muzzle-references at compile time. */
public class MuzzleGradlePlugin implements Plugin {
  static {
    // prevent WeakMap from logging warning while plugin is running
    WeakMap.Provider.registerIfAbsent(
        new WeakMap.Implementation() {
          @Override
          public <K, V> WeakMap<K, V> get() {
            return new WeakMap.MapAdapter<>(Collections.synchronizedMap(new WeakHashMap<K, V>()));
          }
        });
  }

  private static final TypeDescription DefaultInstrumenterTypeDesc =
      new TypeDescription.ForLoadedType(Instrumenter.Default.class);

  @Override
  public boolean matches(TypeDescription target) {
    if (target.isAbstract()) {
      return false;
    }
    // AutoService annotation is not retained at runtime. Check for Instrumenter.Default supertype
    boolean isInstrumenter = false;
    TypeDefinition instrumenter = target.getSuperClass();
    while (instrumenter != null) {
      if (instrumenter.equals(DefaultInstrumenterTypeDesc)) {
        isInstrumenter = true;
        break;
      }
      instrumenter = instrumenter.getSuperClass();
    }
    return isInstrumenter;
  }

  @Override
  public DynamicType.Builder<?> apply(
      DynamicType.Builder<?> builder,
      TypeDescription typeDescription,
      ClassFileLocator classFileLocator) {
    return builder.visit(new MuzzleVisitor());
  }

  @Override
  public void close() {}

  /** Compile-time Optimization used by gradle buildscripts. */
  public static class NoOp implements Plugin {
    @Override
    public boolean matches(TypeDescription target) {
      return false;
    }

    @Override
    public DynamicType.Builder<?> apply(
        DynamicType.Builder<?> builder,
        TypeDescription typeDescription,
        ClassFileLocator classFileLocator) {
      return builder;
    }

    @Override
    public void close() {}
  }
}
