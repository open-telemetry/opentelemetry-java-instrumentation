package io.opentelemetry.helpers.apachehttpclient;

import io.opentelemetry.helpers.core.HttpExtractor;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

public class ApacheHttpClientExtractor implements HttpExtractor<HttpUriRequest, HttpResponse> {

  @Override
  public String getMethod(HttpUriRequest request) {
    return request.getMethod();
  }

  @Override
  public String getUrl(HttpUriRequest request) {
    return request.getURI().toString();
  }

  @Override
  public String getRoute(HttpUriRequest request) {
    return request.getURI().getPath();
  }

  @Override
  public String getUserAgent(HttpUriRequest request) {
    return null;
  }

  @Override
  public String getHttpFlavor(HttpUriRequest request) {
    return "1.1";
  }

  @Override
  public String getClientIp(HttpUriRequest request) {
    return null;
  }

  @Override
  public int getStatusCode(HttpResponse response) {
    if (response == null || response.getStatusLine() == null) {
      return 0;
    } else {
      return response.getStatusLine().getStatusCode();
    }
  }
}
