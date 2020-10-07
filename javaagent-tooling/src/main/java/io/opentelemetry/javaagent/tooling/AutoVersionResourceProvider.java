/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.common.AttributeKey;
import io.opentelemetry.common.Attributes;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.sdk.resources.ResourceProvider;

@AutoService(ResourceProvider.class)
public class AutoVersionResourceProvider extends ResourceProvider {

  private static final AttributeKey<String> TELEMETRY_AUTO_VERSION =
      AttributeKey.stringKey("telemetry.auto.version");

  @Override
  protected Attributes getAttributes() {
    return InstrumentationVersion.VERSION == null
        ? Attributes.empty()
        : Attributes.of(TELEMETRY_AUTO_VERSION, InstrumentationVersion.VERSION);
  }
}
