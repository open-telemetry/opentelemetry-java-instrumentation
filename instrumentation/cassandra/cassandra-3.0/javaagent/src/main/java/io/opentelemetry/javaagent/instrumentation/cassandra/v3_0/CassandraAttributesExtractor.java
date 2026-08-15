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
import javax.annotation.Nullable;

class CassandraAttributesExtractor implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

  private static final ClassValue<Method> speculativeExecutionsMethod =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("getSpeculativeExecutions");
          } catch (NoSuchMethodException ignored) {
            return null;
          }
        }
      };

  private static final ClassValue<Method> hostIdMethod =
      new ClassValue<Method>() {
        @Nullable
        @Override
        protected Method computeValue(Class<?> type) {
          try {
            return type.getMethod("getHostId");
          } catch (NoSuchMethodException ignored) {
            return null;
          }
        }
      };

  @Override
  public void onStart(AttributesBuilder attributes, Context context, CassandraRequest request) {}

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      CassandraRequest request,
      @Nullable ExecutionInfo executionInfo,
      @Nullable Throwable error) {
    if (executionInfo == null) {
      return;
    }

    Host coordinator = executionInfo.getQueriedHost();
    if (coordinator != null) {
      attributes.put(SERVER_ADDRESS, coordinator.getSocketAddress().getHostString());
      attributes.put(SERVER_PORT, coordinator.getSocketAddress().getPort());
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
      }
      String coordinatorId = getCoordinatorId(coordinator);
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_COORDINATOR_ID, coordinatorId);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_COORDINATOR_ID, coordinatorId);
      }
    }

    if (emitStableDatabaseSemconv()) {
      attributes.put(CASSANDRA_CONSISTENCY_LEVEL, request.getConsistencyLevel());
      attributes.put(CASSANDRA_PAGE_SIZE, request.getPageSize());
      attributes.put(CASSANDRA_QUERY_IDEMPOTENT, request.isIdempotent());
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_CASSANDRA_CONSISTENCY_LEVEL, request.getConsistencyLevel());
      attributes.put(DB_CASSANDRA_PAGE_SIZE, request.getPageSize());
      attributes.put(DB_CASSANDRA_IDEMPOTENCE, request.isIdempotent());
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
      Method method = speculativeExecutionsMethod.get(executionInfo.getClass());
      return method == null ? null : (Integer) method.invoke(executionInfo);
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  @Nullable
  private static String getCoordinatorId(Host coordinator) {
    try {
      Method method = hostIdMethod.get(coordinator.getClass());
      Object hostId = method == null ? null : method.invoke(coordinator);
      return hostId == null ? null : hostId.toString();
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }
}
