/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.driver.api.core.metadata.Node;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public class ExperimentalAttributesExtractor
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
    Node coordinator = executionInfo.getCoordinator();
    if (coordinator == null) {
      return;
    }
    SocketAddress address = coordinator.getEndPoint().resolve();
    if (address instanceof InetSocketAddress) {
      attributes.put(
          SemanticAttributes.SERVER_ADDRESS, ((InetSocketAddress) address).getHostName());
      attributes.put(SemanticAttributes.SERVER_PORT, ((InetSocketAddress) address).getPort());
    }
  }
}
