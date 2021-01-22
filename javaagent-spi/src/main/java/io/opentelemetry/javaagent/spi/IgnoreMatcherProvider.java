/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.spi;

import net.bytebuddy.description.type.TypeDescription;

/**
 * {@link IgnoreMatcherProvider} can be used to ignore (or allow) types (e.g. classes or
 * classloaders) from being instrumented. OpenTelemetry agent by default ignores specific set of
 * classes (e.g. {@code org.gradle.*}) and classloaders. This is mainly done to improve startup
 * time, but also to explicitly disable instrumentation of a specific types (e.g. other agents). An
 * implementation of this class can be used to override this behaviour.
 *
 * <p>This is a service provider interface that requires implementations to be registered in {@code
 * META-INF/services} folder. Only a single implementation of this SPI can be provided.
 */
public interface IgnoreMatcherProvider {

  /**
   * Whether to ignore (or allow) type. This method is called for every class, therefore the
   * implementation has to be as efficient as possible.
   *
   * @param target a class.
   * @return the result of the ignore evaluation.
   */
  Result type(TypeDescription target);

  /**
   * Whether to ignore (or allow) classloader. This method is called for every classloader,
   * therefore the implementation has to be as efficient as possible.
   *
   * @param classLoader a classloader.
   * @return the result of the ignore evaluation.
   */
  Result classloader(ClassLoader classLoader);

  /** Result of the ignore evaluation. */
  enum Result {
    /** Default - delegate the evaluation to global ignore matchers from javaagent-tooling. */
    DEFAULT,
    /** Ignore instrumentation for a type. */
    IGNORE,
    /** Allow instrumentation for a type. */
    ALLOW
  }
}
