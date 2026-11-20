/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.internal.Entity;
import io.opentelemetry.sdk.resources.internal.EntityUtil;
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

      if (name == null || version == null) {
        AttributesBuilder attributes =
            Attributes.builder().put(PROCESS_RUNTIME_DESCRIPTION, description);
        attributes.put(PROCESS_RUNTIME_NAME, name);
        attributes.put(PROCESS_RUNTIME_VERSION, version);
        return Resource.create(attributes.build(), SchemaUrls.V1_40_0);
      }
      Attributes descriptive = Attributes.of(PROCESS_RUNTIME_DESCRIPTION, description);
      Attributes id = Attributes.of(PROCESS_RUNTIME_NAME, name, PROCESS_RUNTIME_VERSION, version);
      return EntityUtil.createResource(
          singletonList(
              Entity.builder("process.runtime", id)
                  .setDescription(descriptive)
                  .setSchemaUrl(SchemaUrls.V1_40_0)
                  .build()));
    } catch (SecurityException ignored) {
      return Resource.empty();
    }
  }

  private ProcessRuntimeResource() {}
}
