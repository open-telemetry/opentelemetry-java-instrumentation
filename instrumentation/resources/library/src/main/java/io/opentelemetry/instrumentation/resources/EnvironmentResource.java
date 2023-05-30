/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

public final class EnvironmentResource {

  // Visible for testing
  static final String ATTRIBUTE_PROPERTY = "otel.resource.attributes";
  static final String SERVICE_NAME_PROPERTY = "otel.service.name";

  private static final Resource INSTANCE =
      buildResource(DefaultConfigProperties.create(Collections.emptyMap()));

  /**
   * Returns a new {@link Resource} from the environment. The resource contains attributes parsed
   * from environment variables and system property keys {@code otel.resource.attributes} and {@code
   * otel.service.name}.
   *
   * @return the resource
   */
  public static Resource get() {
    return INSTANCE;
  }

  /**
   * Create a new {@link Resource} from the environment by parsing the {@code config}. The resource
   * contains attributes parsed from keys {@code otel.resource.attributes} and {@code
   * otel.service.name}.
   *
   * @return the resource
   */
  public static Resource create(ConfigProperties configProperties) {
    return buildResource(configProperties);
  }

  static Resource buildResource(ConfigProperties config) {
    return Resource.create(getAttributes(config), ResourceAttributes.SCHEMA_URL);
  }

  // visible for testing
  static Attributes getAttributes(ConfigProperties configProperties) {
    AttributesBuilder resourceAttributes = Attributes.builder();
    try {
      for (Map.Entry<String, String> entry :
          configProperties.getMap(ATTRIBUTE_PROPERTY).entrySet()) {
        resourceAttributes.put(
            entry.getKey(),
            // Attributes specified via otel.resource.attributes follow the W3C Baggage spec and
            // characters outside the baggage-octet range are percent encoded
            // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/resource/sdk.md#specifying-resource-information-via-an-environment-variable
            URLDecoder.decode(entry.getValue(), StandardCharsets.UTF_8.displayName()));
      }
    } catch (UnsupportedEncodingException e) {
      // Should not happen since always using standard charset
      throw new ConfigurationException("Unable to decode resource attributes.", e);
    }
    String serviceName = configProperties.getString(SERVICE_NAME_PROPERTY);
    if (serviceName != null) {
      resourceAttributes.put(ResourceAttributes.SERVICE_NAME, serviceName);
    }
    return resourceAttributes.build();
  }

  private EnvironmentResource() {}
}
