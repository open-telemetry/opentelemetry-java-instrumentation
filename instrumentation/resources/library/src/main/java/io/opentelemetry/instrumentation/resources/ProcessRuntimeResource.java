/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;

/** Factory of a {@link Resource} which provides information about the Java runtime. */
public final class ProcessRuntimeResource {

  // copied from ProcessIncubatingAttributes
  private static final AttributeKey<String> PROCESS_RUNTIME_DESCRIPTION =
      AttributeKey.stringKey("process.runtime.description");
  private static final AttributeKey<String> PROCESS_RUNTIME_NAME =
      AttributeKey.stringKey("process.runtime.name");
  private static final AttributeKey<String> PROCESS_RUNTIME_VERSION =
      AttributeKey.stringKey("process.runtime.version");

  private static final Resource INSTANCE = buildResource();

  /** Returns a factory for a {@link Resource} which provides information about the Java runtime. */
  public static Resource get() {
    return INSTANCE;
  }

  // Visible for testing
  static Resource buildResource() {
    try {
      String name = System.getProperty("java.runtime.name");
      String version = System.getProperty("java.runtime.version");
      String description =
          System.getProperty("java.vm.vendor")
              + " "
              + System.getProperty("java.vm.name")
              + " "
              + System.getProperty("java.vm.version");

      return Resource.create(
          Attributes.of(
              PROCESS_RUNTIME_NAME,
              name,
              PROCESS_RUNTIME_VERSION,
              version,
              PROCESS_RUNTIME_DESCRIPTION,
              description),
          SchemaUrls.V1_24_0);
    } catch (SecurityException ignored) {
      return Resource.empty();
    }
  }

  private ProcessRuntimeResource() {}
}
