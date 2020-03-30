/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import io.opentelemetry.auto.test.base.HttpClientTest
import org.apache.http.HttpResponse
import org.apache.http.concurrent.FutureCallback
import org.apache.http.impl.nio.client.HttpAsyncClients
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CompletableFuture

class ApacheHttpAsyncClientCallbackTest extends HttpClientTest {

  @AutoCleanup
  @Shared
  def client = HttpAsyncClients.createDefault()

  def setupSpec() {
    client.start()
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = new HttpUriRequest(method, uri)
    headers.entrySet().each {
      request.addHeader(new BasicHeader(it.key, it.value))
    }

    def responseFuture = new CompletableFuture<>()

    client.execute(request, new FutureCallback<HttpResponse>() {

      @Override
      void completed(HttpResponse result) {
        callback?.call()
        responseFuture.complete(result.statusLine.statusCode)
      }

      @Override
      void failed(Exception ex) {
        responseFuture.completeExceptionally(ex)
      }

      @Override
      void cancelled() {
        responseFuture.cancel(true)
      }
    })

    return responseFuture.get()
  }

  @Override
  Integer statusOnRedirectError() {
    return 302
  }
}
