import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.client.impl.ClientRequestImpl
import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.jaxrs.v1.JaxRsClientV1Decorator

abstract class JaxRsClientV1Test extends HttpClientTest<JaxRsClientV1Decorator> {

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {

    Client client = Client.create()
    def resource = client.resource(uri)
    headers.each { resource.header(it.key, it.value) }
    def body = BODY_METHODS.contains(method) ? new ClientRequestImpl(uri, method) : null
    ClientResponse response = resource.method(method, body)
    callback?.call()

    return response.status
  }

  @Override
  JaxRsClientV1Decorator decorator() {
    return JaxRsClientV1Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "jax-rs.client.call"
  }

  boolean testRedirects() {
    false
  }
}
