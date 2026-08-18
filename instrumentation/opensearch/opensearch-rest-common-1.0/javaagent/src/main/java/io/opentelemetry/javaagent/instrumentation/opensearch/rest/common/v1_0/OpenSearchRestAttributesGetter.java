/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

final class OpenSearchRestAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRestRequest, OpenSearchRestResponse> {

  private final boolean querySanitizationEnabled;

  OpenSearchRestAttributesGetter(boolean querySanitizationEnabled) {
    this.querySanitizationEnabled = querySanitizationEnabled;
  }

  @Override
  public String getDbSystemName(OpenSearchRestRequest request) {
    return DbSystemNameIncubatingValues.OPENSEARCH;
  }

  @Override
  @Nullable
  public String getDbNamespace(OpenSearchRestRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(OpenSearchRestRequest request) {
    String endpoint = request.getEndpoint();
    if (querySanitizationEnabled) {
      endpoint = OpenSearchEndpointSanitizer.sanitize(request.getMethod(), endpoint);
    }
    return request.getMethod() + " " + endpoint;
  }

  @Override
  public String getDbOperationName(OpenSearchRestRequest request) {
    String operationName =
        OpenSearchEndpointMap.getOperationName(request.getMethod(), request.getEndpoint());
    return operationName != null ? operationName : request.getMethod();
  }

  @Override
  @SuppressWarnings("deprecation") // old database semconv still use db.operation
  public String getDbOperation(OpenSearchRestRequest request) {
    // old semconv output is deliberately left unchanged: it still reports the HTTP method rather
    // than the route-derived operation name
    return request.getMethod();
  }

  @Nullable
  @Override
  public String getErrorType(
      OpenSearchRestRequest request,
      @Nullable OpenSearchRestResponse response,
      @Nullable Throwable error) {

    if (response != null) {
      int statusCode = response.getStatusCode();
      if (statusCode >= 400 || statusCode < 100) {
        return Integer.toString(statusCode);
      }
    }
    return null;
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
    if (response != null) {
      InetAddress address = response.getAddress();
      if (address != null) {
        return address.getHostAddress();
      }
    }
    return null;
  }
}
