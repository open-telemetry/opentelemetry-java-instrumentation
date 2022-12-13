/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Skip muzzle checks for methods annotated with this annotation. */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface NoMuzzle {}
