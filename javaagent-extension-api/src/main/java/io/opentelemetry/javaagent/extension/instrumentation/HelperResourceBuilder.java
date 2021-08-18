/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation;

public interface HelperResourceBuilder {

  /**
   * Registers a resource to be injected in the user's class loader.
   *
   * <p>This is a convenience method for {@code register(resourcePath, resourcePath)}.
   */
  default void register(String resourcePath) {
    register(resourcePath, resourcePath);
  }

  /**
   * Registers a resource to be injected in the user's class loader.
   *
   * <p>{@code agentResourcePath} can be the same as {@code applicationResourcePath}, but it is
   * often desirable to use a slightly different path for {@code agentResourcePath}, so that
   * multiple versions of an instrumentation (each injecting their own version of the resource) can
   * co-exist inside the agent jar file.
   *
   * @param applicationResourcePath the path in the user's class loader at which to inject the
   *     resource
   * @param agentResourcePath the path in the agent class loader from which to get the content for
   *     the resource
   */
  void register(String applicationResourcePath, String agentResourcePath);
}
