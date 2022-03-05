/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class ElasticsearchRestAttributesGetter implements DbClientAttributesGetter<String> {

  @Override
  public String system(String operation) {
    return SemanticAttributes.DbSystemValues.ELASTICSEARCH;
  }

  @Override
  @Nullable
  public String user(String operation) {
    return null;
  }

  @Override
  @Nullable
  public String name(String operation) {
    return null;
  }

  @Override
  @Nullable
  public String connectionString(String operation) {
    return null;
  }

  @Override
  @Nullable
  public String statement(String operation) {
    return null;
  }

  @Override
  @Nullable
  public String operation(String operation) {
    return operation;
  }
}
