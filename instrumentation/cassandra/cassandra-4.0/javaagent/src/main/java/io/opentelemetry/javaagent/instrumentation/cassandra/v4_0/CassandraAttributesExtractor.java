/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

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

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CassandraRequest request) {}

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

    Node coordinator = executionInfo.getCoordinator();
    if (coordinator != null) {
      SocketAddress address = coordinator.getEndPoint().resolve();
      if (address instanceof InetSocketAddress) {
        attributes.put(SERVER_ADDRESS, ((InetSocketAddress) address).getHostString());
        attributes.put(SERVER_PORT, ((InetSocketAddress) address).getPort());
      }
      if (coordinator.getDatacenter() != null) {
        if (emitStableDatabaseSemconv()) {
          attributes.put(CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
        }
        if (emitOldDatabaseSemconv()) {
          attributes.put(DB_CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
        }
      }
      if (coordinator.getHostId() != null) {
        if (emitStableDatabaseSemconv()) {
          attributes.put(CASSANDRA_COORDINATOR_ID, coordinator.getHostId().toString());
        }
        if (emitOldDatabaseSemconv()) {
          attributes.put(DB_CASSANDRA_COORDINATOR_ID, coordinator.getHostId().toString());
        }
      }
    }
    if (emitStableDatabaseSemconv()) {
      attributes.put(
          CASSANDRA_SPECULATIVE_EXECUTION_COUNT, executionInfo.getSpeculativeExecutionCount());
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(
          DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT, executionInfo.getSpeculativeExecutionCount());
    }

    Statement<?> statement = executionInfo.getStatement();
    String consistencyLevel;
    DriverExecutionProfile config =
        request.getSession().getContext().getConfig().getDefaultProfile();
    if (statement.getConsistencyLevel() != null) {
      consistencyLevel = statement.getConsistencyLevel().name();
    } else {
      consistencyLevel = config.getString(DefaultDriverOption.REQUEST_CONSISTENCY);
    }
    if (emitStableDatabaseSemconv()) {
      attributes.put(CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);
    }

    if (statement.getPageSize() > 0) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_PAGE_SIZE, statement.getPageSize());
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_PAGE_SIZE, statement.getPageSize());
      }
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        if (emitStableDatabaseSemconv()) {
          attributes.put(CASSANDRA_PAGE_SIZE, pageSize);
        }
        if (emitOldDatabaseSemconv()) {
          attributes.put(DB_CASSANDRA_PAGE_SIZE, pageSize);
        }
      }
    }

    Boolean idempotent = statement.isIdempotent();
    if (idempotent == null) {
      idempotent = config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE);
    }
    if (emitStableDatabaseSemconv()) {
      attributes.put(CASSANDRA_QUERY_IDEMPOTENT, idempotent);
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_CASSANDRA_IDEMPOTENCE, idempotent);
    }
  }
}
