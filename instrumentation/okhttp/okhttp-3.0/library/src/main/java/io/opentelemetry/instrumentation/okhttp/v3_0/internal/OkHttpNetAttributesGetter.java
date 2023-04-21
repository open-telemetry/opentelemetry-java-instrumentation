/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class OkHttpNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Nullable
  @Override
  public String getProtocolName(Request request, @Nullable Response response) {
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
  public String getProtocolVersion(Request request, @Nullable Response response) {
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
  public String getPeerName(Request request) {
    return request.url().host();
  }

  @Override
  public Integer getPeerPort(Request request) {
    return request.url().port();
  }
}
