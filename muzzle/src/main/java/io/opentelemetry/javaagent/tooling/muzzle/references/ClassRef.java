/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.references;

import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeFields;
import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeFlags;
import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeMethods;
import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeSet;

import java.util.Set;
import javax.annotation.Nullable;

/**
 * Represents a reference to a class used in the instrumentation advice or helper class code (or the
 * helper class itself).
 *
 * <p>This class is used in the auto-generated {@code InstrumentationModule#getMuzzleReferences()}
 * method, it is not meant to be used directly by agent extension developers.
 */
public final class ClassRef {

  private final Set<Source> sources;
  private final Set<Flag> flags;
  private final String className;
  private final String superClassName;
  private final Set<String> interfaceNames;
  private final Set<FieldRef> fields;
  private final Set<MethodRef> methods;

  ClassRef(
      Set<Source> sources,
      Set<Flag> flags,
      String className,
      String superClassName,
      Set<String> interfaceNames,
      Set<FieldRef> fields,
      Set<MethodRef> methods) {
    this.sources = sources;
    this.flags = flags;
    this.className = className;
    this.superClassName = superClassName;
    this.interfaceNames = interfaceNames;
    this.fields = fields;
    this.methods = methods;
  }

  /** Start building a new {@linkplain ClassRef reference}. */
  public static ClassRefBuilder newBuilder(String className) {
    return new ClassRefBuilder(className);
  }

  /** Returns information about code locations where this class was referenced. */
  public Set<Source> getSources() {
    return sources;
  }

  /** Returns modifier flags of this class. */
  public Set<Flag> getFlags() {
    return flags;
  }

  /** Returns the name of this class. */
  public String getClassName() {
    return className;
  }

  /** Returns the name of the super class, if this class extends one; null otherwise. */
  @Nullable
  public String getSuperClassName() {
    return superClassName;
  }

  /** Returns the set of interfaces implemented by this class. */
  public Set<String> getInterfaceNames() {
    return interfaceNames;
  }

  /** Returns the set of references to fields of this class. */
  public Set<FieldRef> getFields() {
    return fields;
  }

  /** Returns the set of references to methods of this class. */
  public Set<MethodRef> getMethods() {
    return methods;
  }

  /**
   * Create a new reference which combines this reference with another reference of the same type.
   *
   * @param anotherReference A reference to the same class.
   * @return a new {@linkplain ClassRef reference} which merges the two references.
   */
  public ClassRef merge(ClassRef anotherReference) {
    if (!anotherReference.getClassName().equals(className)) {
      throw new IllegalStateException("illegal merge " + this + " != " + anotherReference);
    }
    String superName =
        null == this.superClassName ? anotherReference.superClassName : this.superClassName;

    return new ClassRef(
        mergeSet(sources, anotherReference.sources),
        mergeFlags(flags, anotherReference.flags),
        className,
        superName,
        mergeSet(interfaceNames, anotherReference.interfaceNames),
        mergeFields(fields, anotherReference.fields),
        mergeMethods(methods, anotherReference.methods));
  }

  @Override
  public String toString() {
    String extendsPart = superClassName == null ? "" : " extends " + superClassName;
    String implementsPart =
        interfaceNames.isEmpty() ? "" : " implements " + String.join(", ", interfaceNames);
    return getClass().getSimpleName() + ": " + className + extendsPart + implementsPart;
  }
}
