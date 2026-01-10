/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.common;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import org.jspecify.annotations.Nullable;

class ClickHouseAttributesExtractor implements AttributesExtractor<ClickHouseDbRequest, Void> {

  private static final AttributeKey<String> QUERY_ID =
      AttributeKey.stringKey("clickhouse.query_id");

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      ClickHouseDbRequest clickHouseDbRequest) {
    String queryId = clickHouseDbRequest.getQueryId();
    if (queryId != null) {
      attributes.put(QUERY_ID, clickHouseDbRequest.getQueryId());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ClickHouseDbRequest clickHouseDbRequest,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
