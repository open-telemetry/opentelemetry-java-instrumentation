/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class CassandraKeyspaceExtractor
    extends AttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  protected void onStart(AttributesBuilder attributes, CassandraRequest request) {
    attributes.put(
        SemanticAttributes.DB_CASSANDRA_KEYSPACE, request.getSession().getLoggedKeyspace());
  }

  @Override
  protected void onEnd(
      AttributesBuilder attributes,
      CassandraRequest request,
      ExecutionInfo executionInfo,
      @Nullable Throwable error) {}
}
