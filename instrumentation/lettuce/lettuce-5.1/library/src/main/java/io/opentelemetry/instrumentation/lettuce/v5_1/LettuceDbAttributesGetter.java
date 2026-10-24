/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter
    implements DbClientAttributesGetter<LettuceRequest, LettuceResponse> {

  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String REDIS = "redis";

  @Override
  public String getDbSystemName(LettuceRequest request) {
    return REDIS;
  }

  @Nullable
  @Override
  public String getDbNamespace(LettuceRequest request) {
    Long databaseIndex = request.getDatabaseIndex();
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
  }

  @Nullable
  @Override
  public String getDbQueryText(LettuceRequest request) {
    return request.getStatement();
  }

  @Nullable
  @Override
  public String getDbOperationName(LettuceRequest request) {
    return request.getCommand();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      LettuceRequest request, @Nullable LettuceResponse response, @Nullable Throwable error) {
    return response == null ? null : response.getRedisErrorType();
  }

  @Nullable
  @Override
  public String getErrorType(
      LettuceRequest request, @Nullable LettuceResponse response, @Nullable Throwable error) {
    if (response == null) {
      return null;
    }
    String errorType = response.getRedisErrorType();
    // Older Lettuce tracing can report a failure without exposing its exception.
    // Leave exception-class fallback to the shared extractor when an exception is available.
    return errorType == null && response.isFailed() && error == null ? "_OTHER" : errorType;
  }

  @Nullable
  @Override
  public String getServerAddress(LettuceRequest request) {
    InetSocketAddress address = request.getAddress();
    return address != null ? address.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(LettuceRequest request) {
    InetSocketAddress address = request.getAddress();
    return address != null ? address.getPort() : null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      LettuceRequest request, @Nullable LettuceResponse unused) {
    return request.getAddress();
  }
}
