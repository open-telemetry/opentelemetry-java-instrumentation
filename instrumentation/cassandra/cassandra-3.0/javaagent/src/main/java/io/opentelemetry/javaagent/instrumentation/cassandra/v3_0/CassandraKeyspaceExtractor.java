/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class CassandraKeyspaceExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  public void onStart(AttributesBuilder attributes, CassandraRequest request) {
    attributes.put(
        SemanticAttributes.DB_CASSANDRA_KEYSPACE, request.getSession().getLoggedKeyspace());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      CassandraRequest request,
      ExecutionInfo executionInfo,
      @Nullable Throwable error) {}
}
