/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static java.util.concurrent.TimeUnit.SECONDS

import com.squareup.okhttp.Callback
import com.squareup.okhttp.Headers
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import com.squareup.okhttp.internal.http.HttpMethod
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class OkHttp2AsyncTest extends OkHttp2Test {
  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null
    def request = new Request.Builder()
      .url(uri.toURL())
      .method(method, body)
      .headers(Headers.of(HeadersUtil.headersToArray(headers)))
      .build()

    AtomicReference<Response> responseRef = new AtomicReference()
    AtomicReference<Exception> exRef = new AtomicReference()
    def latch = new CountDownLatch(1)

    client.newCall(request).enqueue(new Callback() {
      void onResponse(Response response) {
        responseRef.set(response)
        callback?.call()
        latch.countDown()
      }

      void onFailure(Request req, IOException e) {
        exRef.set(e)
        latch.countDown()
      }
    })
    latch.await(20, SECONDS)
    if (exRef.get() != null) {
      throw exRef.get()
    }
    return responseRef.get().code()
  }

  @Override
  boolean testCausality() {
    false
  }
}
