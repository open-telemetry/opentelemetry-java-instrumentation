/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseNodes;
import com.clickhouse.client.ClickHouseProtocol;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseRequestAccess;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerEndpointUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTargetBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import java.util.Collection;
import javax.annotation.Nullable;

public class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v1-0.5";
  private static final CapturedServerTarget NO_SERVER_TARGET = new CapturedServerTarget(null);
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  private static final VirtualField<ClickHouseNodes, ServerTarget> NODES_SERVER_TARGET =
      VirtualField.find(ClickHouseNodes.class, ServerTarget.class);
  private static final VirtualField<ClickHouseNode, CapturedServerTarget> NODE_SERVER_TARGET =
      VirtualField.find(ClickHouseNode.class, CapturedServerTarget.class);
  private static final VirtualField<ClickHouseRequest<?>, CapturedServerTarget>
      REQUEST_SERVER_TARGET =
          VirtualField.find(ClickHouseRequest.class, CapturedServerTarget.class);

  static {
    instrumenter =
        ClickHouseInstrumenterFactory.createInstrumenter(
            INSTRUMENTER_NAME, ClickHouseErrorCodeExtractor::getErrorCode);
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  public static ServerTarget serverTarget(ClickHouseRequest<?> request) {
    CapturedServerTarget capturedTarget = REQUEST_SERVER_TARGET.get(request);
    if (capturedTarget != null) {
      return capturedTarget.target;
    }
    return uncapturedServerTarget(request);
  }

  @Nullable
  public static ServerTarget peerServerTarget(String host, int port) {
    return ServerTarget.builder(-1).addEndpoint(host, port).build();
  }

  public static void captureConfiguredNodes(
      ClickHouseNodes nodes, Collection<ClickHouseNode> configuredNodes) {
    NODES_SERVER_TARGET.set(nodes, createServerTarget(configuredNodes));
  }

  public static void copyServerTarget(
      ClickHouseRequest<?> request, ClickHouseRequest<?> copiedRequest) {
    CapturedServerTarget capturedTarget = REQUEST_SERVER_TARGET.get(request);
    if (capturedTarget == null) {
      ServerTarget target = uncapturedServerTarget(request);
      capturedTarget = target == null ? NO_SERVER_TARGET : new CapturedServerTarget(target);
    }
    REQUEST_SERVER_TARGET.set(copiedRequest, capturedTarget);
  }

  @Nullable
  private static ServerTarget uncapturedServerTarget(ClickHouseRequest<?> request) {
    ClickHouseNodes nodes = ClickHouseRequestAccess.getNodes(request);
    if (nodes != null) {
      return NODES_SERVER_TARGET.get(nodes);
    }
    ClickHouseNode node = ClickHouseRequestAccess.getDirectNode(request);
    return node == null ? null : nodeServerTarget(node);
  }

  @Nullable
  private static ServerTarget nodeServerTarget(ClickHouseNode node) {
    CapturedServerTarget capturedTarget = NODE_SERVER_TARGET.get(node);
    if (capturedTarget == null) {
      ServerTarget target = createServerTarget(node);
      capturedTarget = target == null ? NO_SERVER_TARGET : new CapturedServerTarget(target);
      NODE_SERVER_TARGET.set(node, capturedTarget);
    }
    return capturedTarget.target;
  }

  @Nullable
  private static ServerTarget createServerTarget(Collection<ClickHouseNode> nodes) {
    ServerTargetBuilder builder = ServerTarget.builder(-1);
    for (ClickHouseNode node : nodes) {
      if (!addEndpoint(builder, node)) {
        return null;
      }
    }
    return builder.build();
  }

  @Nullable
  private static ServerTarget createServerTarget(ClickHouseNode node) {
    ServerTargetBuilder builder = ServerTarget.builder(-1);
    return addEndpoint(builder, node) ? builder.build() : null;
  }

  private static boolean addEndpoint(ServerTargetBuilder builder, ClickHouseNode node) {
    String host = node.getHost();
    if (host.isEmpty()
        || host.indexOf('@') >= 0
        || host.indexOf(',') >= 0
        || host.indexOf('=') >= 0) {
      return false;
    }
    int hostLength = host.length();
    String hostToValidate =
        hostLength > 1 && host.charAt(0) == '[' && host.charAt(hostLength - 1) == ']'
            ? host.substring(1, hostLength - 1)
            : host;
    if (hostToValidate.indexOf(':') >= 0) {
      if (!ServerEndpointUtil.isIpv6Literal(hostToValidate)) {
        return false;
      }
    } else if (hostToValidate.indexOf('%') >= 0) {
      return false;
    }
    ClickHouseProtocol protocol = node.getProtocol();
    int defaultPort =
        node.getConfig().isSsl() ? protocol.getDefaultSecurePort() : protocol.getDefaultPort();
    builder.addEndpoint(host, node.getPort(), defaultPort);
    return true;
  }

  private static class CapturedServerTarget {
    @Nullable private final ServerTarget target;

    private CapturedServerTarget(@Nullable ServerTarget target) {
      this.target = target;
    }
  }

  private ClickHouseClientV1Singletons() {}
}
