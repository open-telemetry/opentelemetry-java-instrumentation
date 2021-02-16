/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
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

class ResteasyProxyClientTest extends HttpClientTest implements AgentTestTrait {
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

  @Override
  boolean testCausality() {
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