/*
 * Copyright The OpenTelemetry Authors
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
import com.squareup.okhttp.Headers
import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.internal.http.HttpMethod
import io.opentelemetry.auto.test.base.HttpClientTest
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.TimeUnit

@Timeout(5)
class OkHttp2Test extends HttpClientTest {
    @Shared
    def client = new OkHttpClient()

    def setupSpec() {
        client.setConnectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        client.setReadTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        client.setWriteTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    @Override
    int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
        def body = HttpMethod.requiresRequestBody(method) ? RequestBody.create(MediaType.parse("text/plain"), "") : null

        def request = new Request.Builder()
                .url(uri.toURL())
                .method(method, body)
                .headers(Headers.of(HeadersUtil.headersToArray(headers)))
                .build()
        def response = client.newCall(request).execute()
        callback?.call()
        return response.code()
    }

    boolean testRedirects() {
        false
    }
}
