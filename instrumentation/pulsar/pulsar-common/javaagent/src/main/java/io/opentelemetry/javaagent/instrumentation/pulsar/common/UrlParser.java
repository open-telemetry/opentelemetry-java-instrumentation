/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.common;

public class UrlParser {

  private UrlParser() {}

  public static UrlData parseUrl(String url) {
    // if there are multiple addresses then they are separated with , or ;
    if (url == null || url.indexOf(',') != -1 || url.indexOf(';') != -1) {
      return null;
    }

    int protocolEnd = url.indexOf("://");
    if (protocolEnd == -1) {
      return null;
    }
    int authorityStart = protocolEnd + 3;
    int authorityEnd = url.indexOf('/', authorityStart);
    if (authorityEnd == -1) {
      authorityEnd = url.length();
    }
    String authority = url.substring(authorityStart, authorityEnd);
    int portStart = authority.indexOf(':');

    String host;
    Integer port;
    if (portStart == -1) {
      host = authority;
      port = null;
    } else {
      host = authority.substring(0, portStart);
      try {
        port = Integer.parseInt(authority.substring(portStart + 1));
      } catch (NumberFormatException exception) {
        port = null;
      }
    }

    return new UrlData(host, port);
  }

  public static class UrlData {
    private final String host;
    private final Integer port;

    UrlData(String host, Integer port) {
      this.host = host;
      this.port = port;
    }

    public String getHost() {
      return host;
    }

    public Integer getPort() {
      return port;
    }
  }
}
