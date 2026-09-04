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
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import java.util.Collection;
import javax.annotation.Nullable;

public class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v1-0.5";
  private static final CapturedServerTarget NO_SERVER_TARGET = new CapturedServerTarget(null);
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  private static final VirtualField<ClickHouseNodes, DbServerTarget> NODES_SERVER_TARGET =
      VirtualField.find(ClickHouseNodes.class, DbServerTarget.class);
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

  public static ClickHouseDbRequest.Endpoint peerEndpoint(String host, int port) {
    DbServerTarget target = DbServerTarget.builder(port).addEndpoint(extractHost(host), -1).build();
    return ClickHouseDbRequest.endpoint(
        target == null ? null : target.getAddress(), target == null ? null : port);
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
    return node == null ? null : createServerTarget(node);
  }

  @Nullable
  private static DbServerTarget createServerTarget(Collection<ClickHouseNode> nodes) {
    DbServerTargetBuilder builder = DbServerTarget.builder(-1);
    for (ClickHouseNode node : nodes) {
      addEndpoint(builder, node);
    }
    return builder.build();
  }

  @Nullable
  private static DbServerTarget createServerTarget(ClickHouseNode node) {
    DbServerTargetBuilder builder = DbServerTarget.builder(-1);
    addEndpoint(builder, node);
    return builder.build();
  }

  private static void addEndpoint(DbServerTargetBuilder builder, ClickHouseNode node) {
    ClickHouseProtocol protocol = node.getProtocol();
    int defaultPort =
        node.getConfig().isSsl() ? protocol.getDefaultSecurePort() : protocol.getDefaultPort();
    builder.addEndpoint(extractHost(node.getHost()), node.getPort(), defaultPort);
  }

  @Nullable
  private static String extractHost(String host) {
    if (host.indexOf('/') >= 0
        || host.indexOf('?') >= 0
        || host.indexOf('#') >= 0
        || host.indexOf(',') >= 0
        || host.indexOf('=') >= 0
        || host.indexOf('%') >= 0
        || host.indexOf('[') >= 0
        || host.indexOf(']') >= 0
        || hasWhitespace(host)) {
      return null;
    }
    return host.indexOf('@') < 0 ? host : null;
  }

  private static boolean hasWhitespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static class CapturedServerTarget {
    @Nullable private final DbServerTarget target;

    private CapturedServerTarget(@Nullable DbServerTarget target) {
      this.target = target;
    }
  }

  private ClickHouseClientV1Singletons() {}
}
