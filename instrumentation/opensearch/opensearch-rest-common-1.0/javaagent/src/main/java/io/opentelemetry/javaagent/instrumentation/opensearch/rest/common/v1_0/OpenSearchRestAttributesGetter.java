/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

final class OpenSearchRestAttributesGetter
    implements DbClientAttributesGetter<OpenSearchRestRequest, OpenSearchRestResponse>,
        AttributesExtractor<OpenSearchRestRequest, OpenSearchRestResponse> {

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
    return request.getMethod() + " " + request.getEndpoint();
  }

  @Override
  public String getDbOperationName(OpenSearchRestRequest request) {
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

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, OpenSearchRestRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      OpenSearchRestRequest request,
      @Nullable OpenSearchRestResponse response,
      @Nullable Throwable error) {
    if (response == null) {
      return;
    }

    String serverAddress = response.getServerAddress();
    attributes.put(SERVER_ADDRESS, serverAddress);
    int serverPort = response.getServerPort();
    if (serverPort > 0) {
      attributes.put(SERVER_PORT, serverPort);
    }
    if (emitStableDatabaseSemconv()) {
      String target = serverPort > 0 ? serverAddress + ":" + serverPort : serverAddress;
      String operation = getDbOperationName(request);
      Span.fromContext(context).updateName(operation != null ? operation + " " + target : target);
    }
  }
}
