package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests.CONNECTION_TIMEOUT_MS;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.ClassInfo;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTests;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;
import java.net.URI;
import java.util.Locale;
import java.util.Map;

class GoogleClientAdapter implements HttpClientTypeAdapter<HttpRequest>  {

  private final RequestSender sender;
  private final HttpRequestFactory requestFactory = new NetHttpTransport().createRequestFactory();

  GoogleClientAdapter(RequestSender sender) {this.sender = sender;}

  @Override
  public HttpRequest buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    GenericUrl genericUrl = new GenericUrl(uri);

    HttpRequest request = requestFactory.buildRequest(method, genericUrl, null);
    request.setConnectTimeout((int) CONNECTION_TIMEOUT_MS);
    if (uri.toString().contains("/read-timeout")) {
      request.setReadTimeout((int) HttpClientTests.READ_TIMEOUT_MS);
    }

    // GenericData::putAll method converts all known http headers to List<String>
    // and lowercase all other headers
    ClassInfo ci = request.getHeaders().getClassInfo();
    headers.forEach(
        (name, value) ->
            request
                .getHeaders()
                .put(name, ci.getFieldInfo(name) != null ? value : value.toLowerCase(Locale.ROOT)));

    request.setThrowExceptionOnExecuteError(false);
    return request;
  }

  @Override
  public int sendRequest(HttpRequest httpRequest, String method, URI uri, Map<String, String> headers) throws Exception {
    HttpResponse response = sender.sendRequest(httpRequest);
    // read request body to avoid broken pipe errors on the server side
    response.parseAsString();
    return response.getStatusCode();
  }
}
