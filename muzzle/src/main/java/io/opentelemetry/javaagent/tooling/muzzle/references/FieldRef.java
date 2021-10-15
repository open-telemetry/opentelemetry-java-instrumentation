/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.references;

import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeFlags;
import static io.opentelemetry.javaagent.tooling.muzzle.references.ReferenceMergeUtil.mergeSet;

import java.util.Set;
import java.util.stream.Collectors;
import org.objectweb.asm.Type;

/**
 * Represents a reference to a field used in the instrumentation advice or helper class code. Part
 * of a {@link ClassRef}.
 *
 * <p>This class is used in the auto-generated {@code InstrumentationModule#getMuzzleReferences()}
 * method, it is not meant to be used directly by agent extension developers.
 */
public final class FieldRef {
  private final Set<Source> sources;
  private final Set<Flag> flags;
  private final String name;
  private final String descriptor;
  private final boolean declared;

  FieldRef(Set<Source> sources, Set<Flag> flags, String name, String descriptor, boolean declared) {
    this.sources = sources;
    this.flags = flags;
    this.name = name;
    this.descriptor = descriptor;
    this.declared = declared;
  }

  /** Returns information about code locations where this field was referenced. */
  public Set<Source> getSources() {
    return sources;
  }

  /** Returns modifier flags of this field. */
  public Set<Flag> getFlags() {
    return flags;
  }

  /** Returns the field name. */
  public String getName() {
    return name;
  }

  /** Returns this field's type descriptor. */
  public String getDescriptor() {
    return descriptor;
  }

  /**
   * Denotes whether this field is declared in the {@linkplain ClassRef class reference} it is a
   * part of. If {@code false} then this field is just used and most likely is declared in the super
   * class.
   */
  public boolean isDeclared() {
    return declared;
  }

  FieldRef merge(FieldRef anotherField) {
    if (!equals(anotherField) || !descriptor.equals(anotherField.descriptor)) {
      throw new IllegalStateException("illegal merge " + this + " != " + anotherField);
    }
    return new FieldRef(
        mergeSet(sources, anotherField.sources),
        mergeFlags(flags, anotherField.flags),
        name,
        descriptor,
        declared || anotherField.declared);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof FieldRef)) {
      return false;
    }
    FieldRef other = (FieldRef) obj;
    return name.equals(other.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  @Override
  public String toString() {
    String modifiers = flags.stream().map(Flag::toString).collect(Collectors.joining(" "));
    String fieldType = Type.getType(getDescriptor()).getClassName();
    return getClass().getSimpleName() + ": " + modifiers + " " + fieldType + " " + name;
  }
}
