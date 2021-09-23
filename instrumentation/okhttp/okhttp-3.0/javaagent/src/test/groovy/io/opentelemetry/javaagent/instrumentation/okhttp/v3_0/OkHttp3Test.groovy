/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0

import io.opentelemetry.instrumentation.okhttp.v3_0.AbstractOkHttp3Test
import io.opentelemetry.instrumentation.test.AgentTestTrait
import okhttp3.Call
import okhttp3.OkHttpClient

import java.util.concurrent.TimeUnit

class OkHttp3Test extends AbstractOkHttp3Test implements AgentTestTrait {

  @Override
  Call.Factory createCallFactory(OkHttpClient.Builder clientBuilder) {
    return clientBuilder.build()
  }

  def "reused builder has one interceptor"() {
    def builder = new OkHttpClient.Builder()
      .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .retryOnConnectionFailure(false)
    when:
    def newClient = builder.build().newBuilder().build()

    then:
    newClient.interceptors().size() == 1
  }

  def "builder created from client has one interceptor"() {
    when:
    def newClient = ((OkHttpClient) client).newBuilder().build()

    then:
    newClient.interceptors().size() == 1
  }

}
