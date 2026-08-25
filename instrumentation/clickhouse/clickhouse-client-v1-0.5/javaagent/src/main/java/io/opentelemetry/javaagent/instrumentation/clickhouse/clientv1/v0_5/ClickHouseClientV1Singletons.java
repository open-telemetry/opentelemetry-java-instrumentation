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
import java.util.Collection;
import javax.annotation.Nullable;

public class ClickHouseClientV1Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v1-0.5";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  // "" marks a configuration that names a single node, which has no group target
  private static final String NO_GROUP = "";

  private static final VirtualField<ClickHouseNodes, String> NODES_ADDRESS_GROUP =
      VirtualField.find(ClickHouseNodes.class, String.class);

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

  /**
   * The complete configured target of a request that was given more than one node, e.g. {@code
   * h1:8123,h2:8123}.
   *
   * <p>The target is rendered once, from the nodes the list was built with, and kept on that list.
   * The nodes a list hands out at query time are only those that are currently healthy and that its
   * load balancing tags select, which is a moving subset of the configuration.
   */
  @Nullable
  public static String serverAddressGroup(ClickHouseRequest<?> request) {
    ClickHouseNodes nodes = ClickHouseRequestAccess.getNodes(request);
    if (nodes == null) {
      return null;
    }
    String addressGroup = NODES_ADDRESS_GROUP.get(nodes);
    return addressGroup == null || NO_GROUP.equals(addressGroup) ? null : addressGroup;
  }

  /** Render and keep the target of a node list, from the nodes it was configured with. */
  public static void captureConfiguredNodes(
      ClickHouseNodes nodes, Collection<ClickHouseNode> configuredNodes) {
    NODES_ADDRESS_GROUP.set(nodes, renderAddressGroup(configuredNodes));
  }

  private static String renderAddressGroup(Collection<ClickHouseNode> nodes) {
    if (nodes.size() < 2) {
      return NO_GROUP;
    }

    StringBuilder addressGroup = new StringBuilder();
    for (ClickHouseNode node : nodes) {
      if (addressGroup.length() > 0) {
        addressGroup.append(',');
      }
      String host = node.getHost();
      if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
        addressGroup.append('[').append(host).append(']');
      } else {
        addressGroup.append(host);
      }
      addressGroup.append(':').append(node.getPort());
    }
    return addressGroup.toString();
  }

  private ClickHouseClientV1Singletons() {}
}
