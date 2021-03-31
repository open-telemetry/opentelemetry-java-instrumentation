/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import java.util.function.Consumer
import ratpack.exec.ExecResult

class RatpackForkedHttpClientTest extends RatpackHttpClientTest {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    ExecResult<Integer> result = exec.yield {
      def resp = client.request(uri) { spec ->
        spec.method(method)
        spec.headers { headersSpec ->
          headers.entrySet().each {
            headersSpec.add(it.key, it.value)
          }
        }
      }
      return resp.fork().map {
        callback?.accept(it.status.code)
        it.status.code
      }
    }
    return result.value
  }
}
