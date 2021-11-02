/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The field or method to which this annotation is applied can only be accessed when holding a
 * particular lock, which may be a built-in (synchronization) lock, or may be an explicit {@link
 * java.util.concurrent.locks.Lock}.
 *
 * <p>The argument determines which lock guards the annotated field or method:
 *
 * <ul>
 *   <li>this : The string literal "this" means that this field is guarded by the class in which it
 *       is defined.
 *   <li>class-name.this : For inner classes, it may be necessary to disambiguate 'this'; the
 *       class-name.this designation allows you to specify which 'this' reference is intended
 *   <li>itself : For reference fields only; the object to which the field refers.
 *   <li>field-name : The lock object is referenced by the (instance or static) field specified by
 *       field-name.
 *   <li>class-name.field-name : The lock object is reference by the static field specified by
 *       class-name.field-name.
 *   <li>method-name() : The lock object is returned by calling the named nil-ary method.
 *   <li>class-name.class : The Class object for the specified class should be used as the lock
 *       object.
 * </ul>
 *
 * <p>This annotation is similar to {@link javax.annotation.concurrent.GuardedBy} but has {@link
 * RetentionPolicy#SOURCE} so it is not in published artifacts. We only apply this to private
 * members, so there is no reason to publish them and we avoid requiring end users to have to depend
 * on the annotations in their own build. See the original <a
 * href="https://github.com/open-telemetry/opentelemetry-java/issues/2897">issue</a> for more info.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface GuardedBy {
  /** The name of the object guarding the target. */
  String value();
}
