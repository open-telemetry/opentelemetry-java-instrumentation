/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.references;

import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Represents the source (file name, line number) of a reference.
 *
 * <p>This class is used in the auto-generated {@code InstrumentationModule#getMuzzleReferences()}
 * method, it is not meant to be used directly by agent extension developers.
 */
public final class Source {
  private final String name;
  private final int line;
  private final boolean inlinedAdvice;

  public Source(String name, int line) {
    this(name, line, false);
  }

  public Source(String name, int line, boolean inlinedAdvice) {
    this.name = name;
    this.line = line;
    this.inlinedAdvice = inlinedAdvice;
  }

  public String getName() {
    return name;
  }

  public int getLine() {
    return line;
  }

  public boolean isInlinedAdvice() {
    return inlinedAdvice;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof Source)) {
      return false;
    }
    Source other = (Source) obj;
    return name.equals(other.name) && line == other.line && inlinedAdvice == other.inlinedAdvice;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, line, inlinedAdvice);
  }

  @Override
  public String toString() {
    return getName() + ":" + getLine();
  }
}
