/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.references;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.objectweb.asm.Type;

/**
 * The builder of {@link ClassRef}.
 *
 * <p>This class is used in the auto-generated {@code InstrumentationModule#getMuzzleReferences()}
 * method, it is not meant to be used directly by agent extension developers.
 */
public final class ClassRefBuilder {

  // this could be exposed as a system property if needed, but for now it's just helpful to be able
  // to change manually here when reviewing/optimizing the generated getMuzzleReferences() method
  static final boolean COLLECT_SOURCES = true;

  private final Set<Source> sources = new LinkedHashSet<>();
  private final Set<Flag> flags = new LinkedHashSet<>();
  private final String className;
  private final Set<String> interfaceNames = new LinkedHashSet<>();
  private final List<FieldRef> fields = new ArrayList<>();
  private final List<MethodRef> methods = new ArrayList<>();

  @Nullable private String superClassName = null;

  ClassRefBuilder(String className) {
    this.className = className;
  }

  public ClassRefBuilder setSuperClassName(String superName) {
    this.superClassName = superName;
    return this;
  }

  public ClassRefBuilder addInterfaceNames(Collection<String> interfaceNames) {
    this.interfaceNames.addAll(interfaceNames);
    return this;
  }

  public ClassRefBuilder addInterfaceName(String interfaceName) {
    interfaceNames.add(interfaceName);
    return this;
  }

  public ClassRefBuilder addSource(String sourceName) {
    return addSource(sourceName, 0);
  }

  public ClassRefBuilder addSource(String sourceName, int line) {
    if (COLLECT_SOURCES) {
      sources.add(new Source(sourceName, line));
    }
    return this;
  }

  public ClassRefBuilder addFlag(Flag flag) {
    flags.add(flag);
    return this;
  }

  public ClassRefBuilder addField(
      Source[] fieldSources,
      Flag[] fieldFlags,
      String fieldName,
      Type fieldType,
      boolean isFieldDeclared) {
    FieldRef field =
        new FieldRef(
            COLLECT_SOURCES ? new LinkedHashSet<>(asList(fieldSources)) : emptySet(),
            new LinkedHashSet<>(asList(fieldFlags)),
            fieldName,
            fieldType.getDescriptor(),
            isFieldDeclared);

    int existingIndex = fields.indexOf(field);
    if (existingIndex == -1) {
      fields.add(field);
    } else {
      fields.set(existingIndex, field.merge(fields.get(existingIndex)));
    }
    return this;
  }

  public ClassRefBuilder addMethod(
      Source[] methodSources,
      Flag[] methodFlags,
      String methodName,
      Type methodReturnType,
      Type... methodArgumentTypes) {
    MethodRef method =
        new MethodRef(
            COLLECT_SOURCES ? new LinkedHashSet<>(asList(methodSources)) : emptySet(),
            new LinkedHashSet<>(asList(methodFlags)),
            methodName,
            Type.getMethodDescriptor(methodReturnType, methodArgumentTypes));

    int existingIndex = methods.indexOf(method);
    if (existingIndex == -1) {
      methods.add(method);
    } else {
      methods.set(existingIndex, method.merge(methods.get(existingIndex)));
    }
    return this;
  }

  public ClassRef build() {
    return new ClassRef(
        sources,
        flags,
        className,
        superClassName,
        interfaceNames,
        new LinkedHashSet<>(fields),
        new LinkedHashSet<>(methods));
  }
}
