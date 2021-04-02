/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import java.util.function.Consumer
import ratpack.exec.Operation
import ratpack.exec.Promise

class RatpackForkedHttpClientTest extends RatpackHttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    exec.yield {
      sendRequest(method, uri, headers)
    }.value
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    exec.execute(Operation.of {
      sendRequest(method, uri, headers).result {
        callback.accept(it.value)
      }
    })
  }

  private Promise<Integer> sendRequest(String method, URI uri, Map<String, String> headers) {
    def resp = client.request(uri) { spec ->
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
