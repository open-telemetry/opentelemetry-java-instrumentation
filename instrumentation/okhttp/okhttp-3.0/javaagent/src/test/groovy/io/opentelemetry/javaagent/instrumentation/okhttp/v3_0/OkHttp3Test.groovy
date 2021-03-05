/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0

import io.opentelemetry.instrumentation.okhttp.v3_0.AbstractOkHttp3Test
import io.opentelemetry.instrumentation.test.AgentTestTrait
import okhttp3.OkHttpClient

class OkHttp3Test extends AbstractOkHttp3Test implements AgentTestTrait {
  @Override
  OkHttpClient.Builder configureClient(OkHttpClient.Builder clientBuilder) {
    return clientBuilder
  }
}
