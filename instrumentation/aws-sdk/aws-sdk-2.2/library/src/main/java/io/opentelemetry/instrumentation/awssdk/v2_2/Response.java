/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import software.amazon.awssdk.core.SdkResponse;
import software.amazon.awssdk.http.SdkHttpResponse;

final class Response {
  private final SdkHttpResponse sdkHttpResponse;
  private final SdkResponse sdkResponse;

  Response(SdkHttpResponse sdkHttpResponse) {
    this(sdkHttpResponse, null);
  }

  Response(SdkHttpResponse sdkHttpResponse, SdkResponse sdkResponse) {
    this.sdkHttpResponse = sdkHttpResponse;
    this.sdkResponse = sdkResponse;
  }

  public SdkHttpResponse getSdkHttpResponse() {
    return sdkHttpResponse;
  }

  public SdkResponse getSdkResponse() {
    return sdkResponse;
  }
}
