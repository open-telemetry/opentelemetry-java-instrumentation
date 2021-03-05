/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import okhttp3.OkHttpClient
import spock.lang.Ignore

// Async test relies on javaagent concurrency instrumentation currently.
@Ignore
class OkHttp3AsyncTest extends AbstractOkHttp3AsyncTest implements LibraryTestTrait {
  @Override
  OkHttpClient.Builder configureClient(OkHttpClient.Builder clientBuilder) {
    return clientBuilder.addInterceptor(OkHttpTracing.create(getOpenTelemetry()).newInterceptor())
  }
}
