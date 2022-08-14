/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.TELEMETRY_SDK_NAME;
import static io.opentelemetry.semconv.resource.attributes.ResourceAttributes.TELEMETRY_SDK_VERSION;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

@AutoService(ResourceProvider.class)
public class AutoResourceProvider implements ResourceProvider {

  private static final AttributeKey<String> TELEMETRY_AUTO_VERSION =
      AttributeKey.stringKey("telemetry.auto.version");
  private static final String TELEMETRY_SDK_NAME_VALUE = "helios-opentelemetry-javaagent";

  @Override
  public Resource createResource(ConfigProperties config) {
    AttributesBuilder attributesBuilder = Attributes.builder();
    attributesBuilder.put(TELEMETRY_SDK_NAME, TELEMETRY_SDK_NAME_VALUE);
    attributesBuilder.put(TELEMETRY_SDK_VERSION, TELEMETRY_SDK_NAME_VALUE);
    attributesBuilder.put(TELEMETRY_AUTO_VERSION, AgentVersion.VERSION);
    Attributes attributes = attributesBuilder.build();
    return AgentVersion.VERSION == null ? Resource.empty() : Resource.create(attributes);
  }
}
