/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RatpackNetClientAttributesGetter
    implements NetClientAttributesGetter<RequestSpec, HttpResponse> {

  @Override
  @Nullable
  public String getPeerName(RequestSpec request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer getPeerPort(RequestSpec request) {
    return request.getUri().getPort();
  }
}
