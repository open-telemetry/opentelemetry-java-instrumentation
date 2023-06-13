/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;

public final class OkHttp2NetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getNetworkProtocolName(Request request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    switch (response.protocol()) {
      case HTTP_1_0:
      case HTTP_1_1:
      case HTTP_2:
        return "http";
      case SPDY_3:
        return "spdy";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request request, @Nullable Response response) {
    if (response == null) {
      return null;
    }
    switch (response.protocol()) {
      case HTTP_1_0:
        return "1.0";
      case HTTP_1_1:
        return "1.1";
      case HTTP_2:
        return "2.0";
      case SPDY_3:
        return "3.1";
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(Request request) {
    return request.url().getHost();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.url().getPort();
  }
}
