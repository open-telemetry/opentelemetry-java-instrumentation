/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.bootstrap.elasticsearch.ElasticsearchConfigAccess;
import java.util.logging.Logger;

final class ElasticsearchRestConfig {

  private static final Logger logger = Logger.getLogger(ElasticsearchRestConfig.class.getName());
  private static final String CAPTURE_SEARCH_QUERY_PROPERTY =
      "otel.instrumentation.elasticsearch.capture-search-query";

  static boolean captureSearchQuery(DeclarativeConfigProperties config, boolean v3Preview) {
    // This stable property must keep working through 2.x. V3-preview reproduces the 3.0 capture
    // behavior and must not read or warn about a property that 3.0 will not recognize.
    if (v3Preview) {
      return true;
    }

    Boolean configured = config.getBoolean("capture_search_query");
    if (configured == null) {
      return false;
    }

    if (ElasticsearchConfigAccess.shouldLogCaptureSearchQueryWarning()) {
      logger.warning(
          "The "
              + CAPTURE_SEARCH_QUERY_PROPERTY
              + " setting and the equivalent declarative configuration property are deprecated"
              + " and will be removed in 3.0. In 3.0, search query bodies are always captured, and"
              + " sanitization remains separately configurable. This setting has no replacement.");
    }
    return configured;
  }

  private ElasticsearchRestConfig() {}
}
