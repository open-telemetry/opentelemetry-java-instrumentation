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

// use JDK field types because separate instrumentation modules write and read this state
public class OpenSearchServerTargets {

  private static final VirtualField<OpenSearchTransport, String> SERVER_ADDRESS =
      VirtualField.find(OpenSearchTransport.class, String.class);
  private static final VirtualField<OpenSearchTransport, Integer> SERVER_PORT =
      VirtualField.find(OpenSearchTransport.class, Integer.class);
  private static final VirtualField<OpenSearchTransport, Boolean> PEER_CAPTURE_ENABLED =
      VirtualField.find(OpenSearchTransport.class, Boolean.class);

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

  public static void enablePeerCapture(OpenSearchTransport transport) {
    PEER_CAPTURE_ENABLED.set(transport, Boolean.TRUE);
  }

  public static boolean isPeerCaptureEnabled(OpenSearchTransport transport) {
    return Boolean.TRUE.equals(PEER_CAPTURE_ENABLED.get(transport));
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
