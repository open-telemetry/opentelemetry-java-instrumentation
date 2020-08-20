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

import io.opentelemetry.auto.test.base.HttpClientTest
import java.nio.charset.StandardCharsets
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.QueryParam
import javax.ws.rs.core.Response
import org.apache.http.client.utils.URLEncodedUtils
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder
import org.jboss.resteasy.specimpl.ResteasyUriBuilder

class ResteasyProxyClientTest extends HttpClientTest {
  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def proxyMethodName = "${method}_${uri.path}".toLowerCase()
      .replace("/", "")
      .replace('-', '_')

    def param = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name())
      .stream().findFirst()
      .map({ it.value })
      .orElse(null)

    def isTestServer = headers.get("is-test-server")

    def proxy = new ResteasyClientBuilder()
      .build()
      .target(new ResteasyUriBuilder().uri(server.address))
      .proxy(ResteasyProxyResource)

    def response = proxy."$proxyMethodName"(param, isTestServer)

    callback?.call()

    return response.status
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    false
  }
}

@Path("")
interface ResteasyProxyResource {
  @GET
  @Path("success")
  Response get_success(@QueryParam("with") String param,
                       @HeaderParam("is-test-server") String isTestServer)

  @POST
  @Path("success")
  Response post_success(@QueryParam("with") String param,
                        @HeaderParam("is-test-server") String isTestServer)

  @PUT
  @Path("success")
  Response put_success(@QueryParam("with") String param,
                       @HeaderParam("is-test-server") String isTestServer)
}