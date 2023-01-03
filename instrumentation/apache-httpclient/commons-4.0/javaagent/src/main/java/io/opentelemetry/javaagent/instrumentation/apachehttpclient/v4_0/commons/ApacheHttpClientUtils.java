package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import static java.util.logging.Level.FINE;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpMessage;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;

public final class ApacheHttpClientUtils {
  private static final Logger logger = Logger.getLogger(ApacheHttpClientUtils.class.getName());

  public ApacheHttpClientUtils() {
  }

  public static List<String> getHeader(HttpMessage httpMessage, String name) {
    return headersToList(httpMessage.getHeaders(name));
  }

  // minimize memory overhead by not using streams
  private static List<String> headersToList(Header[] headers) {
    if (headers == null || headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (int i = 0; i < headers.length; ++i) {
      headersList.add(headers[i].getValue());
    }
    return headersList;
  }

  public static String getFlavor(ProtocolVersion protocolVersion) {
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
    logger.log(FINE, "unexpected http protocol version: {0}", protocolVersion);
    return null;
  }

  public static URI getUri(HttpHost target, HttpRequest httpRequest) {
    URI calculatedUri = getUri(httpRequest);
    if (calculatedUri != null && target != null) {
      calculatedUri = getCalculatedUri(target, calculatedUri);
    }
    return calculatedUri;
  }

  public static Integer getPeerPort(URI uri) {
    return uri == null ? null : uri.getPort();
  }

  public static InetSocketAddress getPeerSocketAddress(HttpHost target) {
    if (target == null) {
      return null;
    }
    InetAddress inetAddress = target.getAddress();
    return inetAddress == null ? null : new InetSocketAddress(inetAddress, target.getPort());
  }

  @Nullable
  private static URI getUri(HttpRequest httpRequest) {
    try {
      // this can be relative or absolute
      return new URI(httpRequest.getRequestLine().getUri());
    } catch (URISyntaxException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }

  @Nullable
  private static URI getCalculatedUri(HttpHost httpHost, URI uri) {
    try {
      return new URI(
          httpHost.getSchemeName(),
          null,
          httpHost.getHostName(),
          httpHost.getPort(),
          uri.getPath(),
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }
}
