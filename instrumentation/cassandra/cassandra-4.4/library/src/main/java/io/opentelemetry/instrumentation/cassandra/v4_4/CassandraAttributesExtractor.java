/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_CASSANDRA_CONSISTENCY_LEVEL =
      AttributeKey.stringKey("db.cassandra.consistency_level");
  private static final AttributeKey<String> DB_CASSANDRA_COORDINATOR_DC =
      AttributeKey.stringKey("db.cassandra.coordinator.dc");
  private static final AttributeKey<String> DB_CASSANDRA_COORDINATOR_ID =
      AttributeKey.stringKey("db.cassandra.coordinator.id");
  private static final AttributeKey<Boolean> DB_CASSANDRA_IDEMPOTENCE =
      AttributeKey.booleanKey("db.cassandra.idempotence");
  private static final AttributeKey<Long> DB_CASSANDRA_PAGE_SIZE =
      AttributeKey.longKey("db.cassandra.page_size");
  private static final AttributeKey<Long> DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT =
      AttributeKey.longKey("db.cassandra.speculative_execution_count");

  // copied from CassandraIncubatingAttributes
  private static final AttributeKey<String> CASSANDRA_CONSISTENCY_LEVEL =
      AttributeKey.stringKey("cassandra.consistency.level");
  private static final AttributeKey<String> CASSANDRA_COORDINATOR_DC =
      AttributeKey.stringKey("cassandra.coordinator.dc");
  private static final AttributeKey<String> CASSANDRA_COORDINATOR_ID =
      AttributeKey.stringKey("cassandra.coordinator.id");
  private static final AttributeKey<Long> CASSANDRA_PAGE_SIZE =
      AttributeKey.longKey("cassandra.page.size");
  private static final AttributeKey<Boolean> CASSANDRA_QUERY_IDEMPOTENT =
      AttributeKey.booleanKey("cassandra.query.idempotent");
  private static final AttributeKey<Long> CASSANDRA_SPECULATIVE_EXECUTION_COUNT =
      AttributeKey.longKey("cassandra.speculative_execution.count");

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, CassandraRequest request) {}

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
      updateServerAddressAndPort(attributes, coordinator);

      String datacenter = coordinator.getDatacenter();
      if (emitStableDatabaseSemconv()) {
        attributes.put(CASSANDRA_COORDINATOR_DC, datacenter);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_CASSANDRA_COORDINATOR_DC, datacenter);
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

    Statement<?> statement = (Statement<?>) executionInfo.getRequest();
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

  static void updateServerAddressAndPort(AttributesBuilder attributes, Node coordinator) {
    EndPoint endPoint = coordinator.getEndPoint();
    if (endPoint instanceof DefaultEndPoint) {
      InetSocketAddress address = ((DefaultEndPoint) endPoint).resolve();
      attributes.put(SERVER_ADDRESS, address.getHostString());
      attributes.put(SERVER_PORT, address.getPort());
    } else if (endPoint instanceof SniEndPoint) {
      // Under SNI (proxied deployments such as DataStax Astra) the client reaches the server
      // through a proxy, and SniEndPoint.resolve() would return the proxy. server.address should
      // be the server behind the proxy, so use the coordinator's own broadcast RPC address, which
      // carries both the address and port with no side effects. resolve() is avoided deliberately:
      // it performs a dns lookup on every call and rotates a shared static counter the driver uses
      // to pick a connection. When the node has not published its RPC address, fall back to the SNI
      // server name (a routing name with no port).
      InetSocketAddress rpcAddress = coordinator.getBroadcastRpcAddress().orElse(null);
      if (rpcAddress != null) {
        attributes.put(SERVER_ADDRESS, rpcAddress.getHostString());
        attributes.put(SERVER_PORT, rpcAddress.getPort());
      } else {
        attributes.put(SERVER_ADDRESS, ((SniEndPoint) endPoint).getServerName());
      }
    }
  }
}
