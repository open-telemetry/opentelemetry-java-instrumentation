/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@ConfigurationProperties(prefix = "otel.resource")
@ConstructorBinding
public class ResourceProperties {

  private final Map<String, String> attributes;

  public ResourceProperties(Map<String, String> attributes) {
    this.attributes = attributes == null ? new HashMap<>() : attributes;
  }

  public Map<String, String> getAttributes() {
    return attributes;
  }
}
