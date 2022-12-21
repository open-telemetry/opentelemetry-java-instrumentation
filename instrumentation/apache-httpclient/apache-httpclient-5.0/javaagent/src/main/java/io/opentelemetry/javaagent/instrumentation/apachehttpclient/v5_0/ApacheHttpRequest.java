package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import java.util.List;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.URIAuthority;

public class ApacheHttpRequest {
  private final HttpRequest httpRequest;

  public ApacheHttpRequest(HttpRequest httpRequest) {this.httpRequest = httpRequest;}

  public String getPeerName() {
    return httpRequest.getAuthority().getHostName();
  }

  public Integer getPeerPort() {
    return httpRequest.getAuthority().getPort();
  }

  public String getMethod() {
    return httpRequest.getMethod();
  }

  public String getUrl() {
    // similar to org.apache.hc.core5.http.message.BasicHttpRequest.getUri()
    // not calling getUri() to avoid unnecessary conversion
    StringBuilder url = new StringBuilder();
    URIAuthority authority = httpRequest.getAuthority();
    if (authority != null) {
      String scheme = httpRequest.getScheme();
      if (scheme != null) {
        url.append(scheme);
        url.append("://");
      } else {
        url.append("http://");
      }
      url.append(authority.getHostName());
      int port = authority.getPort();
      if (port >= 0) {
        url.append(":");
        url.append(port);
      }
    }
    String path = httpRequest.getPath();
    if (path != null) {
      if (url.length() > 0 && !path.startsWith("/")) {
        url.append("/");
      }
      url.append(path);
    } else {
      url.append("/");
    }
    return url.toString();
  }

  public String getFlavor() {
    return ApacheHttpClientHelper.getFlavor(httpRequest.getVersion());
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientHelper.getHeader(httpRequest, name);
  }

  public void setHeader(String key, String value) {
    httpRequest.setHeader(key, value);
  }
}
