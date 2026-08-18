/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.v1_0;

/**
 * Runs the shared OpenSearch REST tests with query sanitization disabled via {@code
 * -Dotel.instrumentation.opensearch.query-sanitization.enabled=false}, verifying that the raw
 * document identifier is left in {@code db.query.text}.
 */
class OpenSearchRestQuerySanitizationDisabledTest extends OpenSearchRestTest {

  @Override
  protected boolean isQuerySanitizationEnabled() {
    return false;
  }
}
