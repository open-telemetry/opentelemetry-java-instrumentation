/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

final class OpenSearchJavaNetResponseAttributesGetter
    implements NetworkAttributesGetter<OpenSearchJavaRequest, OpenSearchJavaResponse> {

  @Nullable
  @Override
  public String getNetworkType(
      OpenSearchJavaRequest request, @Nullable OpenSearchJavaResponse response) {
    if (response == null) {
      return null;
    }
    InetAddress address = response.getAddress();
    if (address instanceof Inet4Address) {
      return "ipv4";
    } else if (address instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      OpenSearchJavaRequest request, @Nullable OpenSearchJavaResponse response) {
    if (response != null && response.getAddress() != null) {
      return response.getAddress().getHostAddress();
    }
    return null;
  }
}
