/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.elasticsearch.ElasticsearchQuerySanitizerAccess;
import io.opentelemetry.javaagent.tooling.BeforeAgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

/**
 * Publishes the Elasticsearch search query sanitizer. This runs in the agent class loader, which is
 * the only place jackson-core is visible, and hands the sanitizer to a bootstrap class that the
 * instrumentation can reach from the application class loader.
 */
@AutoService(BeforeAgentListener.class)
public class ElasticsearchQuerySanitizerInstaller implements BeforeAgentListener {

  @Override
  public void beforeAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk) {
    ElasticsearchQuerySanitizerAccess.internalSetSanitizer(
        new JacksonElasticsearchQuerySanitizer());
  }
}
