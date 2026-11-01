/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_QUERY_IDEMPOTENT;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_SPECULATIVE_EXECUTION_COUNT;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, CassandraResponse> {

  @Nullable
  private static final Method GET_SPECULATIVE_EXECUTIONS =
      findMethod(ExecutionInfo.class, "getSpeculativeExecutions");

  @Override
  public void onStart(AttributesBuilder attributes, Context context, CassandraRequest request) {
    attributes.put(CASSANDRA_CONSISTENCY_LEVEL, request.getConsistencyLevel());
    attributes.put(CASSANDRA_PAGE_SIZE, request.getPageSize());
    attributes.put(CASSANDRA_QUERY_IDEMPOTENT, request.isIdempotent());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CassandraRequest request,
      @Nullable CassandraResponse response,
      @Nullable Throwable error) {
    if (response == null) {
      return;
    }

    ExecutionInfo executionInfo = response.getExecutionInfo();
    if (executionInfo == null) {
      return;
    }

    Host coordinator = executionInfo.getQueriedHost();
    if (coordinator != null) {
      attributes.put(CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      String coordinatorId = CassandraEndPoints.getHostId(coordinator);
      attributes.put(CASSANDRA_COORDINATOR_ID, coordinatorId);
    }

    Integer speculativeExecutionCount = getSpeculativeExecutionCount(executionInfo);
    if (speculativeExecutionCount != null) {
      attributes.put(CASSANDRA_SPECULATIVE_EXECUTION_COUNT, speculativeExecutionCount);
    }
  }

  @Nullable
  private static Integer getSpeculativeExecutionCount(ExecutionInfo executionInfo) {
    try {
      return GET_SPECULATIVE_EXECUTIONS == null
          ? null
          : (Integer) GET_SPECULATIVE_EXECUTIONS.invoke(executionInfo);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findMethod(Class<?> type, String name) {
    try {
      return type.getMethod(name);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }
}
