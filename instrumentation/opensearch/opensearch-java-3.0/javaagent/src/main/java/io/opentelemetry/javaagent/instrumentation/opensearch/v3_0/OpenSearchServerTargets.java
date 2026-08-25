/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import java.util.List;
import javax.annotation.Nullable;
import org.opensearch.client.transport.OpenSearchTransport;

/**
 * Keeps the target a transport was built with, so that a request records the whole configured
 * target rather than the node that happened to answer.
 *
 * <p>The target is held in fields of types the JDK already provides, because the transport that
 * records it and the request that reads it are covered by separate instrumentation modules.
 */
public final class OpenSearchServerTargets {

  private static final VirtualField<OpenSearchTransport, String> SERVER_ADDRESS =
      VirtualField.find(OpenSearchTransport.class, String.class);
  private static final VirtualField<OpenSearchTransport, Integer> SERVER_PORT =
      VirtualField.find(OpenSearchTransport.class, Integer.class);

  public static void capture(
      OpenSearchTransport transport, @Nullable List<OpenSearchServerTarget.Endpoint> endpoints) {
    capture(transport, OpenSearchServerTarget.of(endpoints));
  }

  public static void capture(
      OpenSearchTransport transport, @Nullable OpenSearchServerTarget target) {
    if (target == null) {
      return;
    }
    SERVER_ADDRESS.set(transport, target.getAddress());
    SERVER_PORT.set(transport, target.getPort());
  }

  @Nullable
  public static String address(OpenSearchTransport transport) {
    return SERVER_ADDRESS.get(transport);
  }

  @Nullable
  public static Integer port(OpenSearchTransport transport) {
    return SERVER_PORT.get(transport);
  }

  private OpenSearchServerTargets() {}
}
