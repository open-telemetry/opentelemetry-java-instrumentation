/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0

import io.opentelemetry.instrumentation.okhttp.v3_0.AbstractOkHttp3Test
import io.opentelemetry.instrumentation.test.AgentTestTrait
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jetbrains.annotations.NotNull

class OkHttp3Test extends AbstractOkHttp3Test implements AgentTestTrait {
  @Override
  OkHttpClient.Builder configureClient(OkHttpClient.Builder clientBuilder) {
    return clientBuilder
  }

  /**
   * Makes sure that the builder interceptors are the same before and after invoking
   * {@link OkHttpClient.Builder#build}. This guarantees that there will be no duplicate tracing
   * interceptors and that the tracing interceptor is not in the middle of the chain.
   */
  def "builder only contains the original interceptors after build"() {
    setup:
    def customInterceptor = new TestInterceptor()
    def clientBuilder = new OkHttpClient.Builder().addInterceptor(customInterceptor)
    def originalInterceptors = new ArrayList<>(clientBuilder.interceptors())

    when:
    clientBuilder.build()

    then:
    clientBuilder.interceptors() == originalInterceptors
  }

  /**
   * Makes sure that the tracing interceptor is not present in the builder returned by
   * {@link OkHttpClient#newBuilder()}. This reduces the chance of the corner case where when
   * building a client, the builder already contains the tracing interceptor.
   */
  def "builder created from client does not contain tracing interceptor"() {
    setup:
    def customInterceptor = new TestInterceptor()
    def clientBuilder = new OkHttpClient.Builder().addInterceptor(customInterceptor)
    def originalInterceptors = new ArrayList<>(clientBuilder.interceptors())

    when:
    def builderFromClient = clientBuilder.build().newBuilder()

    then:
    builderFromClient.interceptors() == originalInterceptors
  }

  /**
   * Tests the corner case where the tracing interceptor has been manually added to the builder by
   * accessing it from the interceptors list of an existing client. In this case, a client created
   * from that builder should only have one tracing interceptor and it must be in the end, but the
   * builder state should still be the same before and after building.
   */
  def "tracing interceptor in the end for client even if it is in the middle for builder"() {
    setup:
    def customInterceptor = new TestInterceptor()
    def clientBuilder = new OkHttpClient.Builder().addInterceptor(customInterceptor)

    when:
    def client = clientBuilder.build()

    then:
    clientBuilder.interceptors().size() == 1
    clientBuilder.interceptors().get(0).is(customInterceptor)
    client.interceptors().size() == 2
    client.interceptors().get(0).is(customInterceptor)
    client.interceptors().get(1).is(OkHttp3Interceptors.TRACING_INTERCEPTOR)

    when:
    def otherInterceptor = new TestInterceptor()
    def newClientBuilder = new OkHttpClient.Builder()
      .addInterceptor(client.interceptors().get(0))
      .addInterceptor(client.interceptors().get(1))
      .addInterceptor(client.interceptors().get(1))
      .addInterceptor(otherInterceptor)
    def originalNewInterceptors = newClientBuilder.interceptors()
    def newClient = newClientBuilder.build()

    then:
    newClientBuilder.interceptors() == originalNewInterceptors
    newClient.interceptors().size() == 3
    newClient.interceptors().get(0).is(customInterceptor)
    newClient.interceptors().get(1).is(otherInterceptor)
    newClient.interceptors().get(2).is(OkHttp3Interceptors.TRACING_INTERCEPTOR)
  }

  private static class TestInterceptor implements Interceptor {

    @Override
    Response intercept(@NotNull Chain chain) throws IOException {
      return chain.proceed(chain.request())
    }
  }
}
