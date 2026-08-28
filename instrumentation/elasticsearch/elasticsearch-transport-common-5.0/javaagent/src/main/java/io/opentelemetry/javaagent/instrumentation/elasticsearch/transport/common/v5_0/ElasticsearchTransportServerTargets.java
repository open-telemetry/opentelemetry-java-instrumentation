/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.elasticsearch.client.support.AbstractClient;

// freeze the first configured target before later client reconfiguration can change it
public class ElasticsearchTransportServerTargets {
  private static final String NO_TARGET = "";

  private static final VirtualField<AbstractClient, String> SERVER_ADDRESS =
      VirtualField.find(AbstractClient.class, String.class);
  private static final VirtualField<AbstractClient, Integer> SERVER_PORT =
      VirtualField.find(AbstractClient.class, Integer.class);
  private static final Object captureLock = new Object();

  public static boolean isCaptured(AbstractClient client) {
    return SERVER_ADDRESS.get(client) != null;
  }

  public static void capture(
      AbstractClient client,
      @Nullable List<ElasticsearchTransportServerTarget.Endpoint> endpoints) {
    ElasticsearchTransportServerTarget target = ElasticsearchTransportServerTarget.of(endpoints);
    synchronized (captureLock) {
      if (isCaptured(client)) {
        return;
      }
      if (target == null) {
        if (endpoints == null) {
          SERVER_ADDRESS.set(client, NO_TARGET);
        }
        return;
      }
      SERVER_PORT.set(client, target.getPort());
      SERVER_ADDRESS.set(client, target.getAddress());
    }
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
