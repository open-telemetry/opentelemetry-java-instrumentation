package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;

class HttpUtils {
  private static final Logger logger = Logger.getLogger(HttpUtils.class.getName());

  public static void setHeader(@Nullable HttpRequest httpRequest, String key, String value) {
    if (httpRequest == null) {
      return;
    }
    httpRequest.setHeader(key, value);
  }

  @Nullable
  public static String getFlavor(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    ProtocolVersion protocolVersion = getVersion(httpRequest, httpResponse);
    if (protocolVersion == null) {
      return null;
    }
    String protocol = protocolVersion.getProtocol();
    if (!protocol.equals("HTTP")) {
      return null;
    }
    int major = protocolVersion.getMajor();
    int minor = protocolVersion.getMinor();
    if (major == 1 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    if (major == 1 && minor == 1) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
    if (major == 2 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    logger.log(Level.FINE, "unexpected http protocol version: {0}", protocolVersion);
    return null;
  }

  // minimize memory overhead by not using streams
  public static List<String> headersToList(Header[] headers) {
    if (headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (Header header : headers) {
      headersList.add(header.getValue());
    }
    return headersList;
  }

  public static List<String> getHeader(HttpRequest httpRequest, String name) {
    return HttpUtils.headersToList(httpRequest.getHeaders(name));
  }

  private static ProtocolVersion getVersion(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null && response != null) {
      protocolVersion = response.getVersion();
    }
    return protocolVersion;
  }
}
