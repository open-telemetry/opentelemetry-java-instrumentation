/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;

@AutoService(ResourceProvider.class)
public class AutoVersionResourceProvider implements ResourceProvider {

  private static final AttributeKey<String> TELEMETRY_AUTO_VERSION =
      AttributeKey.stringKey("telemetry.auto.version");

  @Override
  public Resource createResource(ConfigProperties config) {
    return InstrumentationVersion.VERSION == null
        ? Resource.empty()
        : Resource.create(Attributes.of(TELEMETRY_AUTO_VERSION, InstrumentationVersion.VERSION));
  }
}
