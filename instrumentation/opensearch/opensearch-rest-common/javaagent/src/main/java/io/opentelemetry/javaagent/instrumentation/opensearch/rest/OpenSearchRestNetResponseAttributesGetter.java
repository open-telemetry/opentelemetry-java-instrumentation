/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;
import org.opensearch.client.Response;

@SuppressWarnings("deprecation") // have to use the deprecated Net*AttributesGetter for now
final class OpenSearchRestNetResponseAttributesGetter
    implements io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
        OpenSearchRestRequest, Response> {

  @Nullable
  @Override
  public String getSockFamily(
      OpenSearchRestRequest elasticsearchRestRequest, @Nullable Response response) {
    if (response != null && response.getHost().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

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
  public String getServerAddress(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Integer getServerPort(OpenSearchRestRequest request) {
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
