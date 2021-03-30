/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3

import com.google.common.util.concurrent.MoreExecutors
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.WebClientBuilder
import com.linecorp.armeria.common.AggregatedHttpResponse
import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.RequestHeaders
import io.opentelemetry.context.Context
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import spock.lang.Shared

abstract class AbstractArmeriaHttpClientTest extends HttpClientTest {

  abstract WebClientBuilder configureClient(WebClientBuilder clientBuilder)

  @Shared
  def client = configureClient(WebClient.builder()).build()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    HttpRequest request = HttpRequest.of(
      RequestHeaders.builder(HttpMethod.valueOf(method), uri.toString())
        .set(headers.entrySet())
        .build())

    AtomicReference<AggregatedHttpResponse> responseRef = new AtomicReference<>()
    AtomicReference<Throwable> exRef = new AtomicReference<>()
    def latch = new CountDownLatch(1)
    client.execute(request).aggregate().whenCompleteAsync(new BiConsumer<AggregatedHttpResponse, Throwable>() {
      @Override
      void accept(AggregatedHttpResponse aggregatedHttpResponse, Throwable throwable) {
        if (throwable != null) {
          exRef.set(throwable)
        } else {
          responseRef.set(aggregatedHttpResponse)
        }
        callback?.call()
        latch.countDown()
      }
    }, Context.current().wrap(MoreExecutors.directExecutor()))

    latch.await(30, TimeUnit.SECONDS)
    if (exRef.get() != null) {
      throw exRef.get()
    }
    return responseRef.get().status().code()
  }

  // Not supported yet: https://github.com/line/armeria/issues/2489
  @Override
  boolean testRedirects() {
    false
  }

  // TODO(anuraaga): Enable after fixing the test https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2344
  @Override
  boolean testRemoteConnection() {
    false
  }
}
