/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.CassandraIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
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
        attributes.put(
            ServerAttributes.SERVER_ADDRESS, ((InetSocketAddress) address).getHostString());
        attributes.put(ServerAttributes.SERVER_PORT, ((InetSocketAddress) address).getPort());
      }
      if (coordinator.getDatacenter() != null) {
        if (SemconvStability.emitStableDatabaseSemconv()) {
          attributes.put(
              CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
        }
        if (SemconvStability.emitOldDatabaseSemconv()) {
          attributes.put(
              DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC, coordinator.getDatacenter());
        }
      }
      if (coordinator.getHostId() != null) {
        if (SemconvStability.emitStableDatabaseSemconv()) {
          attributes.put(
              CassandraIncubatingAttributes.CASSANDRA_COORDINATOR_ID,
              coordinator.getHostId().toString());
        }
        if (SemconvStability.emitOldDatabaseSemconv()) {
          attributes.put(
              DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID,
              coordinator.getHostId().toString());
        }
      }
    }
    if (SemconvStability.emitStableDatabaseSemconv()) {
      attributes.put(
          CassandraIncubatingAttributes.CASSANDRA_SPECULATIVE_EXECUTION_COUNT,
          executionInfo.getSpeculativeExecutionCount());
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      attributes.put(
          DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT,
          executionInfo.getSpeculativeExecutionCount());
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
    if (SemconvStability.emitStableDatabaseSemconv()) {
      attributes.put(CassandraIncubatingAttributes.CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      attributes.put(DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);
    }

    if (statement.getPageSize() > 0) {
      if (SemconvStability.emitStableDatabaseSemconv()) {
        attributes.put(CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE, statement.getPageSize());
      }
      if (SemconvStability.emitOldDatabaseSemconv()) {
        attributes.put(DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE, statement.getPageSize());
      }
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        if (SemconvStability.emitStableDatabaseSemconv()) {
          attributes.put(CassandraIncubatingAttributes.CASSANDRA_PAGE_SIZE, pageSize);
        }
        if (SemconvStability.emitOldDatabaseSemconv()) {
          attributes.put(DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE, pageSize);
        }
      }
    }

    Boolean idempotent = statement.isIdempotent();
    if (idempotent == null) {
      idempotent = config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE);
    }
    if (SemconvStability.emitStableDatabaseSemconv()) {
      attributes.put(CassandraIncubatingAttributes.CASSANDRA_QUERY_IDEMPOTENT, idempotent);
    }
    if (SemconvStability.emitOldDatabaseSemconv()) {
      attributes.put(DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE, idempotent);
    }
  }
}
