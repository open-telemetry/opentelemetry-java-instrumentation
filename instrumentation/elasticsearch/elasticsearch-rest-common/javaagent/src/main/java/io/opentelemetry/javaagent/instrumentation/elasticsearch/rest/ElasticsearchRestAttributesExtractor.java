/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

final class ElasticsearchRestAttributesExtractor extends DbAttributesExtractor<String, Response> {
  @Override
  protected String system(String operation) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  protected String user(String operation) {
    return null;
  }

  @Override
  @Nullable
  protected String name(String operation) {
    return null;
  }

  @Override
  @Nullable
  protected String connectionString(String operation) {
    return null;
  }

  @Override
  @Nullable
  protected String statement(String operation) {
    return null;
  }

  @Override
  @Nullable
  protected String operation(String operation) {
    return operation;
  }
}
