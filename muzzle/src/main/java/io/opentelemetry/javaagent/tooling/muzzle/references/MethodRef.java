/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.references;

import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeFlags;
import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeSet;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

/**
 * Represents a reference to a method used in the instrumentation advice or helper class code. Part
 * of a {@link ClassRef}.
 *
 * <p>This class is used in the auto-generated {@code InstrumentationModule#getMuzzleReferences()}
 * method, it is not meant to be used directly by agent extension developers.
 */
public final class MethodRef {
  private final Set<Source> sources;
  private final Set<Flag> flags;
  private final String name;
  private final String descriptor;

  MethodRef(Set<Source> sources, Set<Flag> flags, String name, String descriptor) {
    this.sources = sources;
    this.flags = flags;
    this.name = name;
    this.descriptor = descriptor;
  }

  /** Returns information about code locations where this method was referenced. */
  public Set<Source> getSources() {
    return sources;
  }

  /** Returns modifier flags of this method. */
  public Set<Flag> getFlags() {
    return flags;
  }

  /** Returns the method name. */
  public String getName() {
    return name;
  }

  /** Returns this method's type descriptor. */
  public String getDescriptor() {
    return descriptor;
  }

  MethodRef merge(MethodRef anotherMethod) {
    if (!equals(anotherMethod)) {
      throw new IllegalStateException("illegal merge " + this + " != " + anotherMethod);
    }
    return new MethodRef(
        mergeSet(sources, anotherMethod.sources),
        mergeFlags(flags, anotherMethod.flags),
        name,
        descriptor);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof MethodRef)) {
      return false;
    }
    MethodRef other = (MethodRef) obj;
    return name.equals(other.name) && descriptor.equals(other.descriptor);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, descriptor);
  }

  @Override
  public String toString() {
    Type methodType = Type.getMethodType(getDescriptor());
    String returnType = methodType.getReturnType().getClassName();
    String modifiers = flags.stream().map(Flag::toString).collect(Collectors.joining(" "));
    String parameters =
        Stream.of(methodType.getArgumentTypes())
            .map(Type::getClassName)
            .collect(Collectors.joining(", ", "(", ")"));
    return getClass().getSimpleName()
        + ": "
        + modifiers
        + " "
        + returnType
        + " "
        + name
        + parameters;
  }
}
