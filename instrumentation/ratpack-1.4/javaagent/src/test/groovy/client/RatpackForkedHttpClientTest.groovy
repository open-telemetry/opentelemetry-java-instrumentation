/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import java.time.Duration
import ratpack.exec.Promise
import ratpack.http.client.HttpClient

class RatpackForkedHttpClientTest extends RatpackHttpClientTest {

  @Override
  Promise<Integer> internalSendRequest(HttpClient client, String method, URI uri, Map<String, String> headers) {
    def resp = client.request(uri) { spec ->
      // Connect timeout for the whole client was added in 1.5 so we need to add timeout for each request
      spec.connectTimeout(Duration.ofSeconds(2))
      spec.method(method)
      spec.headers { headersSpec ->
        headers.entrySet().each {
          headersSpec.add(it.key, it.value)
        }
      }
    }
    return resp.fork().map {
      it.status.code
    }
  }
}
