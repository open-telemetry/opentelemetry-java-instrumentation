/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.elasticsearch.client.support.AbstractClient;

/**
 * Keeps the target a transport client is configured with.
 *
 * <p>A transport client is given its addresses after it is built, so the target is read the first
 * time the client is used and then left alone. The addresses a client reports are the ones it was
 * configured with, never the ones it discovered by sniffing the cluster, and freezing them keeps a
 * client that is reconfigured later from reporting two identities.
 *
 * <p>A client that talks to a node in the same process has no address and stays without a target.
 */
public class ElasticsearchTransportServerTargets {

  // marks a client whose target has been read and that has none, e.g. an in-process node client
  private static final String NO_TARGET = "";

  private static final VirtualField<AbstractClient, String> SERVER_ADDRESS =
      VirtualField.find(AbstractClient.class, String.class);
  private static final VirtualField<AbstractClient, Integer> SERVER_PORT =
      VirtualField.find(AbstractClient.class, Integer.class);

  public static boolean isCaptured(AbstractClient client) {
    return SERVER_ADDRESS.get(client) != null;
  }

  public static void capture(
      AbstractClient client,
      @Nullable List<ElasticsearchTransportServerTarget.Endpoint> endpoints) {
    ElasticsearchTransportServerTarget target = ElasticsearchTransportServerTarget.of(endpoints);
    if (target == null) {
      SERVER_ADDRESS.set(client, NO_TARGET);
      return;
    }
    SERVER_PORT.set(client, target.getPort());
    SERVER_ADDRESS.set(client, target.getAddress());
  }

  @Nullable
  public static String address(AbstractClient client) {
    String address = SERVER_ADDRESS.get(client);
    return NO_TARGET.equals(address) ? null : address;
  }

  @Nullable
  public static Integer port(AbstractClient client) {
    return address(client) == null ? null : SERVER_PORT.get(client);
  }

  private ElasticsearchTransportServerTargets() {}
}
