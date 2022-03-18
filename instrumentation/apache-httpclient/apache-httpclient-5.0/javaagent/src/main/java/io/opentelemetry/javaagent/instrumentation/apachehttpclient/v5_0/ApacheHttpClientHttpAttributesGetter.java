/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.net.URIAuthority;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ClassicHttpRequest, HttpResponse> {

  private static final Logger logger =
      Logger.getLogger(ApacheHttpClientHttpAttributesGetter.class.getName());

  @Override
  public String method(ClassicHttpRequest request) {
    return request.getMethod();
  }

  @Override
  public String url(ClassicHttpRequest request) {
    // similar to org.apache.hc.core5.http.message.BasicHttpRequest.getUri()
    // not calling getUri() to avoid unnecessary conversion
    StringBuilder url = new StringBuilder();
    URIAuthority authority = request.getAuthority();
    if (authority != null) {
      String scheme = request.getScheme();
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
    String path = request.getPath();
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

  @Override
  public List<String> requestHeader(ClassicHttpRequest request, String name) {
    return headersToList(request.getHeaders(name));
  }

  @Override
  @Nullable
  public Long requestContentLength(ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ClassicHttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(ClassicHttpRequest request, HttpResponse response) {
    return response.getCode();
  }

  @Override
  @Nullable
  public String flavor(ClassicHttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
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
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("unexpected http protocol version: " + protocolVersion);
    }
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLength(ClassicHttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(ClassicHttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ClassicHttpRequest request, HttpResponse response, String name) {
    return headersToList(response.getHeaders(name));
  }

  // minimize memory overhead by not using streams
  private static List<String> headersToList(Header[] headers) {
    if (headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (int i = 0; i < headers.length; ++i) {
      headersList.add(headers[i].getValue());
    }
    return headersList;
  }
}
