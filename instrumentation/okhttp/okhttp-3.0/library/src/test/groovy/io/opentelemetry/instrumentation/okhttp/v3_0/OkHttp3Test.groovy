/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0

import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.LibraryTestTrait
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

class OkHttp3Test extends AbstractOkHttp3Test implements LibraryTestTrait {
  @Override
  OkHttpClient.Builder configureClient(OkHttpClient.Builder clientBuilder) {
    return clientBuilder
      // The double "new Dispatcher" style is the simplest way to decorate the default executor.
      .dispatcher(new Dispatcher(Context.taskWrapping(new Dispatcher().executorService())))
      .addInterceptor(OkHttpTracing.create(getOpenTelemetry()).newInterceptor())
  }

  // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
  @Override
  boolean testWithClientParent() {
    false
  }
}
