/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbResponseStatusUtil.dbResponseStatusCode;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

final class OpenSearchRestAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRestRequest, OpenSearchRestResponse> {

  @Override
  public String getDbSystemName(OpenSearchRestRequest request) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.OPENSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(OpenSearchRestRequest request) {
    return request.getMethod() + " " + request.getEndpoint();
  }

  @Override
  @Nullable
  public String getDbOperationName(OpenSearchRestRequest request) {
    return request.getMethod();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      @Nullable OpenSearchRestResponse response, @Nullable Throwable error) {
    return response != null ? dbResponseStatusCode(response.getStatusCode()) : null;
  }

  @Nullable
  @Override
  public String getNetworkType(
      OpenSearchRestRequest request, @Nullable OpenSearchRestResponse response) {
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
      OpenSearchRestRequest request, @Nullable OpenSearchRestResponse response) {
    if (response != null && response.getAddress() != null) {
      return response.getAddress().getHostAddress();
    }
    return null;
  }
}
