/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import com.datastax.driver.core.ExecutionInfo;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.ServerAttributes;
import javax.annotation.Nullable;

public class CassandraAttributesExtractor
    implements AttributesExtractor<CassandraRequest, ExecutionInfo> {
  @Override
  public void onStart(AttributesBuilder attributes, Context context, CassandraRequest request) {}

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
    attributes.put(
        ServerAttributes.SERVER_ADDRESS,
        executionInfo.getQueriedHost().getSocketAddress().getHostString());
    attributes.put(
        ServerAttributes.SERVER_PORT, executionInfo.getQueriedHost().getSocketAddress().getPort());
  }
}
