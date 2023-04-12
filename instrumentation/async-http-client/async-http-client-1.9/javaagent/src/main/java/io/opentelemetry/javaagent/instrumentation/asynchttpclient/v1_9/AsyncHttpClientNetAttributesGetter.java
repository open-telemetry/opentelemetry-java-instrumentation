/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

final class AsyncHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getProtocolName(Request request, @Nullable Response response) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(Request request, @Nullable Response response) {
    return null;
  }

  @Override
  public String getPeerName(Request request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer getPeerPort(Request request) {
    return request.getUri().getPort();
  }
}
