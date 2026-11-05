/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

final class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {

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
      String datacenter = coordinator.getDatacenter();
      attributes.put(CASSANDRA_COORDINATOR_DC, datacenter);
      if (coordinator.getHostId() != null) {
        attributes.put(CASSANDRA_COORDINATOR_ID, coordinator.getHostId().toString());
      }
    }
    attributes.put(
        CASSANDRA_SPECULATIVE_EXECUTION_COUNT, executionInfo.getSpeculativeExecutionCount());

    Statement<?> statement = (Statement<?>) executionInfo.getRequest();
    String consistencyLevel;
    DriverExecutionProfile config =
        request.getSession().getContext().getConfig().getDefaultProfile();
    if (statement.getConsistencyLevel() != null) {
      consistencyLevel = statement.getConsistencyLevel().name();
    } else {
      consistencyLevel = config.getString(DefaultDriverOption.REQUEST_CONSISTENCY);
    }
    attributes.put(CASSANDRA_CONSISTENCY_LEVEL, consistencyLevel);

    if (statement.getPageSize() > 0) {
      attributes.put(CASSANDRA_PAGE_SIZE, statement.getPageSize());
    } else {
      int pageSize = config.getInt(DefaultDriverOption.REQUEST_PAGE_SIZE);
      if (pageSize > 0) {
        attributes.put(CASSANDRA_PAGE_SIZE, pageSize);
      }
    }

    Boolean idempotent = statement.isIdempotent();
    if (idempotent == null) {
      idempotent = config.getBoolean(DefaultDriverOption.REQUEST_DEFAULT_IDEMPOTENCE);
    }
    attributes.put(CASSANDRA_QUERY_IDEMPOTENT, idempotent);
  }
}
