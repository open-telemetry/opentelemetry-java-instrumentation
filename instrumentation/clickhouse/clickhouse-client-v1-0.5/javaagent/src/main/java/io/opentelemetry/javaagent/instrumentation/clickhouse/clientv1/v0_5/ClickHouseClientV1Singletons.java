/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv1.v0_5;

import com.clickhouse.client.ClickHouseException;
import com.clickhouse.client.ClickHouseNode;
import com.clickhouse.client.ClickHouseNodes;
import com.clickhouse.client.ClickHouseRequest;
import com.clickhouse.client.ClickHouseRequestAccess;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import javax.annotation.Nullable;

public class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v1-0.5";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  private static final VirtualField<ClickHouseNodes, ServerTarget> NODES_SERVER_TARGET =
      VirtualField.find(ClickHouseNodes.class, ServerTarget.class);
  private static final VirtualField<ClickHouseRequest<?>, ServerTarget> REQUEST_SERVER_TARGET =
      VirtualField.find(ClickHouseRequest.class, ServerTarget.class);

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
  public static String serverAddressGroup(ClickHouseRequest<?> request) {
    return serverTarget(request).addressGroup;
  }

  @Nullable
  public static String serverAddress(ClickHouseRequest<?> request) {
    return serverTarget(request).address;
  }

  @Nullable
  public static Integer serverPort(ClickHouseRequest<?> request) {
    return serverTarget(request).port;
  }

  public static void captureConfiguredNodes(
      ClickHouseNodes nodes, Collection<ClickHouseNode> configuredNodes) {
    NODES_SERVER_TARGET.set(nodes, ServerTarget.create(configuredNodes));
  }

  public static void copyServerTarget(
      ClickHouseRequest<?> request, ClickHouseRequest<?> copiedRequest) {
    REQUEST_SERVER_TARGET.set(copiedRequest, serverTarget(request));
  }

  private static ServerTarget serverTarget(ClickHouseRequest<?> request) {
    ServerTarget target = REQUEST_SERVER_TARGET.get(request);
    if (target != null) {
      return target;
    }
    ClickHouseNodes nodes = ClickHouseRequestAccess.getNodes(request);
    if (nodes != null) {
      target = NODES_SERVER_TARGET.get(nodes);
      return target == null ? ServerTarget.UNCONFIGURED : target;
    }
    ClickHouseNode node = ClickHouseRequestAccess.getDirectNode(request);
    return node == null ? ServerTarget.UNCONFIGURED : ServerTarget.create(node);
  }

  private static class ServerTarget {

    private static final ServerTarget UNCONFIGURED = new ServerTarget(null, null, null);

    @Nullable private final String address;
    @Nullable private final Integer port;
    @Nullable private final String addressGroup;

    private ServerTarget(
        @Nullable String address, @Nullable Integer port, @Nullable String addressGroup) {
      this.address = address;
      this.port = port;
      this.addressGroup = addressGroup;
    }

    private static ServerTarget create(Collection<ClickHouseNode> nodes) {
      if (nodes.isEmpty()) {
        return UNCONFIGURED;
      }
      if (nodes.size() == 1) {
        return create(nodes.iterator().next());
      }

      StringBuilder addressGroup = new StringBuilder();
      for (ClickHouseNode node : nodes) {
        if (addressGroup.length() > 0) {
          addressGroup.append(',');
        }
        String host = sanitizeHost(node.getHost());
        if (host == null) {
          return UNCONFIGURED;
        }
        appendAddress(addressGroup, host, node.getPort());
      }
      return new ServerTarget(null, null, addressGroup.toString());
    }

    private static ServerTarget create(ClickHouseNode node) {
      String host = sanitizeHost(node.getHost());
      return host == null ? UNCONFIGURED : new ServerTarget(host, node.getPort(), null);
    }

    private static void appendAddress(StringBuilder addressGroup, String host, int port) {
      if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
        addressGroup.append('[').append(host).append(']');
      } else {
        addressGroup.append(host);
      }
      addressGroup.append(':').append(port);
    }

    @Nullable
    private static String sanitizeHost(String host) {
      int at = host.indexOf('@');
      if (at >= 0) {
        if (at != host.lastIndexOf('@')) {
          return null;
        }
        host = host.substring(at + 1);
      }
      if (host.isEmpty()
          || host.indexOf('/') >= 0
          || host.indexOf('?') >= 0
          || host.indexOf('#') >= 0
          || host.indexOf('=') >= 0) {
        return null;
      }

      if (host.startsWith("[")) {
        if (!host.endsWith("]")) {
          return null;
        }
        host = host.substring(1, host.length() - 1);
      } else if (host.indexOf('[') >= 0 || host.indexOf(']') >= 0) {
        return null;
      }

      for (int i = 0; i < host.length(); i++) {
        if (Character.isWhitespace(host.charAt(i))) {
          return null;
        }
      }
      if (host.indexOf(':') >= 0) {
        try {
          if (new URI(null, null, host, -1, null, null, null).getHost() == null) {
            return null;
          }
        } catch (URISyntaxException ignored) {
          return null;
        }
      }
      return host;
    }
  }

  private ClickHouseClientV1Singletons() {}
}
