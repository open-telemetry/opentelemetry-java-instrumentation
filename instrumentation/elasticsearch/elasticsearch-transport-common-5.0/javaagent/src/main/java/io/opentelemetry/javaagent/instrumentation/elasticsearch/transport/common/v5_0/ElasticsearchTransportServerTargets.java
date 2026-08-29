/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.List;
import javax.annotation.Nullable;
import org.elasticsearch.client.support.AbstractClient;

public class ElasticsearchTransportServerTargets {
  private static final VirtualField<AbstractClient, ElasticsearchTransportServerTarget>
      SERVER_TARGET =
          VirtualField.find(AbstractClient.class, ElasticsearchTransportServerTarget.class);

  public static void update(
      AbstractClient client,
      @Nullable List<ElasticsearchTransportServerTarget.Endpoint> endpoints) {
    SERVER_TARGET.set(client, ElasticsearchTransportServerTarget.of(endpoints));
  }

  @Nullable
  public static String address(AbstractClient client) {
    ElasticsearchTransportServerTarget target = SERVER_TARGET.get(client);
    return target == null ? null : target.getAddress();
  }

  @Nullable
  public static Integer port(AbstractClient client) {
    ElasticsearchTransportServerTarget target = SERVER_TARGET.get(client);
    return target == null ? null : target.getPort();
  }

  private ElasticsearchTransportServerTargets() {}
}
