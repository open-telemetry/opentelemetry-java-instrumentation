/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.field;

import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Represents a "virtual" field of type {@code F} that is added to type {@code T} in the runtime.
 *
 * <p>A virtual field has similar semantics to a weak-keys strong-values map: the value will be
 * garbage collected when their owner instance is collected. It is discouraged to use a virtual
 * field for keeping values that might reference their key, as it may cause memory leaks.
 *
 * @param <T> The type that will contain the new virtual field.
 * @param <F> The field type that'll be added to {@code T}.
 */
// we're using an abstract class here so that we can call static find() in pre-jdk8 advice classes
public abstract class VirtualField<T, F> {

  /**
   * Finds a {@link VirtualField} instance for given {@code type} and {@code fieldType}.
   *
   * <p>Conceptually this can be thought of as a map lookup to fetch a second level map given {@code
   * type}.
   *
   * <p>In runtime, when using the javaagent, the <em>calls</em> to this method are rewritten to
   * something more performant while injecting advice into a method.
   *
   * <p>When using this method outside of Advice method, the {@link VirtualField} should be looked
   * up once and stored in a field to avoid repeatedly calling this method.
   *
   * @param type The type that will contain the new virtual field.
   * @param fieldType The field type that will be added to {@code type}.
   */
  public static <U extends T, T, F> VirtualField<U, F> find(Class<T> type, Class<F> fieldType) {
    return RuntimeVirtualFieldSupplier.get().find(type, fieldType);
  }

  /** Gets the value of this virtual field. */
  @Nullable
  public abstract F get(T object);

  /** Sets the new value of this virtual field. */
  public abstract void set(T object, @Nullable F fieldValue);

  /**
   * Sets the new value of this virtual field if the current value is {@code null}.
   *
   * @return The old field value if it was present, or the result of evaluating passed {@code
   *     fieldValueSupplier}.
   */
  public abstract F computeIfNull(T object, Supplier<F> fieldValueSupplier);
}
