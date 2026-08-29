/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_QUERY_IDEMPOTENT;
import static io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes.CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;

import com.datastax.driver.core.ExecutionInfo;
import com.datastax.driver.core.Host;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation") // using deprecated semconv
class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, CassandraResponse> {

  @Nullable
  private static final Method GET_SPECULATIVE_EXECUTIONS =
      findMethod(ExecutionInfo.class, "getSpeculativeExecutions");

  @Override
  public void onStart(AttributesBuilder attributes, Context context, CassandraRequest request) {
    if (emitStableDatabaseSemconv()) {
      CassandraConfiguredTarget configuredTarget = request.getConfiguredTarget();
      if (configuredTarget != null) {
        configuredTarget.put(attributes);
      }
      attributes.put(CASSANDRA_CONSISTENCY_LEVEL, request.getConsistencyLevel());
      attributes.put(CASSANDRA_PAGE_SIZE, request.getPageSize());
      attributes.put(CASSANDRA_QUERY_IDEMPOTENT, request.isIdempotent());
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_CASSANDRA_CONSISTENCY_LEVEL, request.getConsistencyLevel());
      attributes.put(DB_CASSANDRA_PAGE_SIZE, request.getPageSize());
      attributes.put(DB_CASSANDRA_IDEMPOTENCE, request.isIdempotent());
    }
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

    if (emitOldDatabaseSemconv() && !emitStableDatabaseSemconv()) {
      InetSocketAddress coordinatorAddress = response.getPeerAddress();
      if (coordinatorAddress != null) {
        attributes.put(SERVER_ADDRESS, coordinatorAddress.getHostString());
        attributes.put(SERVER_PORT, coordinatorAddress.getPort());
      }
    }

    ExecutionInfo executionInfo = response.getExecutionInfo();
    if (executionInfo == null) {
      return;
    }

    Host coordinator = executionInfo.getQueriedHost();
    if (coordinator != null) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      }
      String coordinatorId = CassandraEndPoints.getHostId(coordinator);
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_COORDINATOR_ID, coordinatorId);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_COORDINATOR_ID, coordinatorId);
      }
    }

    Integer speculativeExecutionCount = getSpeculativeExecutionCount(executionInfo);
    if (speculativeExecutionCount != null) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_SPECULATIVE_EXECUTION_COUNT, speculativeExecutionCount);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT, speculativeExecutionCount);
      }
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
