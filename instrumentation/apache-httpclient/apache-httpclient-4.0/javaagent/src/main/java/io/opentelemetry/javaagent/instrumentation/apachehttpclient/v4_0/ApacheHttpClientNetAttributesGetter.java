/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String getProtocolName(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getProtocolName();
  }

  @Override
  public String getProtocolVersion(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getProtocolVersion();
  }

  @Override
  @Nullable
  public String getPeerName(ApacheHttpClientRequest request) {
    return request.getPeerName();
  }

  @Override
  public Integer getPeerPort(ApacheHttpClientRequest request) {
    return request.getPeerPort();
  }
}
