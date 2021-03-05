/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import okhttp3.OkHttpClient

class OkHttp3Test extends AbstractOkHttp3Test implements LibraryTestTrait {
  @Override
  OkHttpClient.Builder configureClient(OkHttpClient.Builder clientBuilder) {
    return clientBuilder.addInterceptor(OkHttpTracing.create(getOpenTelemetry()).newInterceptor())
  }
}
