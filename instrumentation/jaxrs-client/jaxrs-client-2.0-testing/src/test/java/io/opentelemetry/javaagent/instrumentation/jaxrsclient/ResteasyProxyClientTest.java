/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTestOptions;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.specimpl.ResteasyUriBuilder;
import org.junit.jupiter.api.extension.RegisterExtension;

class ResteasyProxyClientTest extends AbstractHttpClientTest<ResteasyProxyResource> {
  @RegisterExtension
  static final InstrumentationExtension testing = HttpClientInstrumentationExtension.forAgent();

  static ResteasyClient client = new ResteasyClientBuilder().connectionPoolSize(4).build();

  @Override
  public ResteasyProxyResource buildRequest(String method, URI uri, Map<String, String> headers) {
    return client
        .target(new ResteasyUriBuilder().uri(resolveAddress("")))
        .proxy(ResteasyProxyResource.class);
  }

  @Override
  public int sendRequest(
      ResteasyProxyResource proxy, String method, URI uri, Map<String, String> headers) {
    String proxyMethodName =
        (method + "_" + uri.getPath()).toLowerCase(Locale.ROOT).replace("/", "").replace('-', '_');

    String param =
        URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name()).stream()
            .findFirst()
            .map(NameValuePair::getValue)
            .orElse(null);

    String isTestServer = headers.get("is-test-server");
    String requestId = headers.get("test-request-id");

    Response response;
    if (proxyMethodName.equals("get_success")) {
      response = proxy.get_success(param, isTestServer, requestId);
    } else if (proxyMethodName.equals("post_success")) {
      response = proxy.post_success(param, isTestServer, requestId);
    } else if (proxyMethodName.equals("put_success")) {
      response = proxy.put_success(param, isTestServer, requestId);
    } else if (proxyMethodName.equals("get_error")) {
      response = proxy.get_error(param, isTestServer, requestId);
    } else if (proxyMethodName.equals("get_client_error")) {
      response = proxy.get_client_error(param, isTestServer, requestId);
    } else {
      throw new IllegalArgumentException("Unknown method: " + proxyMethodName);
    }
    response.close();
    return response.getStatus();
  }

  @Override
  protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
    super.configure(optionsBuilder);
    optionsBuilder.setTestCallback(false);
    optionsBuilder.setTestConnectionFailure(false);
    optionsBuilder.setTestNonStandardHttpMethod(false);
    optionsBuilder.setTestReadTimeout(false);
    optionsBuilder.setTestRemoteConnection(false);
    optionsBuilder.setTestRedirects(false);
    optionsBuilder.setTestCaptureHttpHeaders(false);
    optionsBuilder.disableTestSpanEndsAfter();
  }
}
