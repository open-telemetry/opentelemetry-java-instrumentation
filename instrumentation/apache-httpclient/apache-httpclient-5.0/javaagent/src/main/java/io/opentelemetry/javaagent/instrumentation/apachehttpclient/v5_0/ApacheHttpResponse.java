package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import java.util.List;
import org.apache.hc.core5.http.HttpResponse;

public class ApacheHttpResponse {
  private final HttpResponse httpResponse;

  public ApacheHttpResponse(HttpResponse httpResponse) {this.httpResponse = httpResponse;}

  public int getStatusCode() {
    return httpResponse.getCode();
  }

  public String getFlavor() {
    return ApacheHttpClientHelper.getFlavor(httpResponse.getVersion());
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientHelper.getHeader(httpResponse, name);
  }
}
