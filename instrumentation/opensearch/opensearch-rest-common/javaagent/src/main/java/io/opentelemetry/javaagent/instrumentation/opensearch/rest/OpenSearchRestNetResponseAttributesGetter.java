/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;
import org.opensearch.client.Response;

final class OpenSearchRestNetResponseAttributesGetter
    implements NetworkAttributesGetter<OpenSearchRestRequest, Response> {

  @Nullable
  @Override
  public String getNetworkType(OpenSearchRestRequest request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    InetAddress address = response.getHost().getAddress();
    if (address instanceof Inet4Address) {
      return "ipv4";
    } else if (address instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(OpenSearchRestRequest request, @Nullable Response response) {
    if (response != null && response.getHost().getAddress() != null) {
      return response.getHost().getAddress().getHostAddress();
    }
    return null;
  }
}
