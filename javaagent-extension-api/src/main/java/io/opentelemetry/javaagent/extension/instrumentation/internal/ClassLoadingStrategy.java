/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time. <br>
 * Explicitly defines the classloader that needs to be used to load classes.
 *
 * <ul>
 *   <li>when using inline instrumentation, this is ignored and behavior is equivalent to {@link
 *       ClassLoadingTarget#INSTRUMENTATION_TARGET} as classes are injected into the instrumented CL
 *   <li>when using indy instrumentation and not explicitly set, the behavior is equivalent to an
 *       explicit {@link ClassLoadingTarget#INSTRUMENTATION_ISOLATED}
 *   <li>when using indy and classes/packages need to be shared across multiple isolated
 *       classloaders, the {@link ClassLoadingTarget#INSTRUMENTATION_SHARED} should be used
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.PACKAGE})
public @interface ClassLoadingStrategy {

  ClassLoadingTarget value() default ClassLoadingTarget.INSTRUMENTATION_ISOLATED;
}
