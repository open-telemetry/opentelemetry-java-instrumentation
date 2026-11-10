/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Accumulates the documented shape of a single event (a log record carrying an event name) across
 * all the times it was emitted during a test class: the union of its attributes.
 *
 * <p>Events are keyed by {@link Key}, so the same event name emitted at two different severities is
 * accumulated as two separate shapes.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CollectedEvent {

  private final Set<InternalAttributeKeyImpl<?>> attributeKeys = new LinkedHashSet<>();

  public void addAttributeKey(InternalAttributeKeyImpl<?> key) {
    attributeKeys.add(key);
  }

  /** Returns the union of the attribute keys seen on this event, each carrying its own type. */
  public Set<InternalAttributeKeyImpl<?>> getAttributeKeys() {
    return attributeKeys;
  }

  /**
   * The identity of an event: its name together with its severity. The same event name can be
   * emitted at more than one severity by a single instrumentation scope - the default {@code
   * exception} event, for example, is {@code ERROR} for server and consumer operations and {@code
   * WARN} for client and producer operations - and those are distinct documented shapes.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static final class Key implements Comparable<Key> {

    private final String name;
    @Nullable private final String severity;

    public Key(String name, @Nullable String severity) {
      this.name = name;
      this.severity = severity;
    }

    public String getName() {
      return name;
    }

    @Nullable
    public String getSeverity() {
      return severity;
    }

    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Key)) {
        return false;
      }
      Key other = (Key) o;
      return name.equals(other.name) && Objects.equals(severity, other.severity);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, severity);
    }

    @Override
    public int compareTo(Key other) {
      int result = name.compareTo(other.name);
      if (result != 0) {
        return result;
      }
      if (Objects.equals(severity, other.severity)) {
        return 0;
      }
      if (severity == null) {
        return -1;
      }
      if (other.severity == null) {
        return 1;
      }
      return severity.compareTo(other.severity);
    }

    @Override
    public String toString() {
      return severity == null ? name : name + "/" + severity;
    }
  }
}
