package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.http.HttpMethod;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientResult;
import io.opentelemetry.instrumentation.testing.junit.http.HttpClientTypeAdapter;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

class OkHttp2TypeAdapter implements HttpClientTypeAdapter<Request> {
  private final OkHttpClient client;
  private final OkHttpClient clientWithReadTimeout;

  public OkHttp2TypeAdapter(OkHttpClient client, OkHttpClient clientWithReadTimeout) {
    this.client = client;
    this.clientWithReadTimeout = clientWithReadTimeout;
  }

  @Override
  public Request buildRequest(String method, URI uri, Map<String, String> headers)
      throws Exception {
    RequestBody body =
        HttpMethod.requiresRequestBody(method)
            ? RequestBody.create(MediaType.parse("text/plain"), "")
            : null;
    Request.Builder request = new Request.Builder().url(uri.toURL()).method(method, body);
    headers.forEach(request::header);
    return request.build();
  }

  @Override
  public int sendRequest(Request request, String method, URI uri, Map<String, String> headers)
      throws Exception {
    return getClient(uri).newCall(request).execute().code();
  }

  @Override
  public void sendRequestWithCallback(Request request, String method, URI uri,
      Map<String, String> headers, HttpClientResult httpClientResult) throws Exception {
    getClient(uri)
        .newCall(request)
        .enqueue(
            new Callback() {
              @Override
              public void onFailure(Request req, IOException e) {
                httpClientResult.complete(e);
              }

              @Override
              public void onResponse(Response response) {
                httpClientResult.complete(response.code());
              }
            });
  }

  private OkHttpClient getClient(URI uri) {
    if (uri.toString().contains("/read-timeout")) {
      return clientWithReadTimeout;
    }
    return client;
  }

}
