/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseException;
import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseNodes;
import com.clickhouse.client.ClickHouseProtocol;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseRequestAccess;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseEndpointUtil;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import java.util.Collection;
import javax.annotation.Nullable;

public class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v1-0.5";
  private static final CapturedServerTarget NO_SERVER_TARGET = new CapturedServerTarget(null);
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  private static final VirtualField<ClickHouseNodes, DbServerTarget> NODES_SERVER_TARGET =
      VirtualField.find(ClickHouseNodes.class, DbServerTarget.class);
  private static final VirtualField<ClickHouseNode, CapturedServerTarget> NODE_SERVER_TARGET =
      VirtualField.find(ClickHouseNode.class, CapturedServerTarget.class);
  private static final VirtualField<ClickHouseRequest<?>, CapturedServerTarget>
      REQUEST_SERVER_TARGET =
          VirtualField.find(ClickHouseRequest.class, CapturedServerTarget.class);

  static {
    instrumenter =
        ClickHouseInstrumenterFactory.createInstrumenter(
            INSTRUMENTER_NAME,
            error -> {
              if (error instanceof ClickHouseException) {
                int errorCode = ((ClickHouseException) error).getErrorCode();
                return errorCode == 0 ? null : Integer.toString(errorCode);
              }
              return null;
            });
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  public static DbServerTarget serverTarget(ClickHouseRequest<?> request) {
    CapturedServerTarget capturedTarget = REQUEST_SERVER_TARGET.get(request);
    if (capturedTarget != null) {
      return capturedTarget.target;
    }
    return uncapturedServerTarget(request);
  }

  public static void captureConfiguredNodes(
      ClickHouseNodes nodes, Collection<ClickHouseNode> configuredNodes) {
    NODES_SERVER_TARGET.set(nodes, createServerTarget(configuredNodes));
  }

  public static void copyServerTarget(
      ClickHouseRequest<?> request, ClickHouseRequest<?> copiedRequest) {
    CapturedServerTarget capturedTarget = REQUEST_SERVER_TARGET.get(request);
    if (capturedTarget == null) {
      DbServerTarget target = uncapturedServerTarget(request);
      capturedTarget = target == null ? NO_SERVER_TARGET : new CapturedServerTarget(target);
    }
    REQUEST_SERVER_TARGET.set(copiedRequest, capturedTarget);
  }

  @Nullable
  private static DbServerTarget uncapturedServerTarget(ClickHouseRequest<?> request) {
    ClickHouseNodes nodes = ClickHouseRequestAccess.getNodes(request);
    if (nodes != null) {
      return NODES_SERVER_TARGET.get(nodes);
    }
    ClickHouseNode node = ClickHouseRequestAccess.getDirectNode(request);
    return node == null ? null : nodeServerTarget(node);
  }

  @Nullable
  private static DbServerTarget nodeServerTarget(ClickHouseNode node) {
    CapturedServerTarget capturedTarget = NODE_SERVER_TARGET.get(node);
    if (capturedTarget == null) {
      DbServerTarget target = createServerTarget(node);
      capturedTarget = target == null ? NO_SERVER_TARGET : new CapturedServerTarget(target);
      NODE_SERVER_TARGET.set(node, capturedTarget);
    }
    return capturedTarget.target;
  }

  @Nullable
  private static DbServerTarget createServerTarget(Collection<ClickHouseNode> nodes) {
    DbServerTargetBuilder builder = DbServerTarget.builder(-1);
    for (ClickHouseNode node : nodes) {
      if (!addEndpoint(builder, node)) {
        return null;
      }
    }
    return builder.build();
  }

  @Nullable
  private static DbServerTarget createServerTarget(ClickHouseNode node) {
    DbServerTargetBuilder builder = DbServerTarget.builder(-1);
    return addEndpoint(builder, node) ? builder.build() : null;
  }

  private static boolean addEndpoint(DbServerTargetBuilder builder, ClickHouseNode node) {
    String host = node.getHost();
    if (host.indexOf(':') >= 0 && !ClickHouseEndpointUtil.isIpv6Literal(host)) {
      return false;
    }
    ClickHouseProtocol protocol = node.getProtocol();
    int defaultPort =
        node.getConfig().isSsl() ? protocol.getDefaultSecurePort() : protocol.getDefaultPort();
    builder.addEndpoint(host, node.getPort(), defaultPort);
    return true;
  }

  private static class CapturedServerTarget {
    @Nullable private final DbServerTarget target;

    private CapturedServerTarget(@Nullable DbServerTarget target) {
      this.target = target;
    }
  }

  private ClickHouseClientV1Singletons() {}
}
