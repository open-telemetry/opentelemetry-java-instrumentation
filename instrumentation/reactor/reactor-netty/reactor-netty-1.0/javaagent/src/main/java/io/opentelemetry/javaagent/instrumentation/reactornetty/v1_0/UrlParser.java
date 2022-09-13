/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import javax.annotation.Nullable;

class UrlParser {

  @Nullable
  static String getHost(String url) {

    int schemeEndIndex = url.indexOf(':');
    if (schemeEndIndex == -1) {
      // not a valid url
      return null;
    }

    int len = url.length();
    if (len <= schemeEndIndex + 2
        || url.charAt(schemeEndIndex + 1) != '/'
        || url.charAt(schemeEndIndex + 2) != '/') {
      // has no authority component
      return null;
    }

    // look for the end of the host:
    //   ':' ==> start of port, or
    //   '/', '?', '#' ==> start of path
    int index;
    for (index = schemeEndIndex + 3; index < len; index++) {
      char c = url.charAt(index);
      if (c == ':' || c == '/' || c == '?' || c == '#') {
        break;
      }
    }
    String host = url.substring(schemeEndIndex + 3, index);
    return host.isEmpty() ? null : host;
  }

  @Nullable
  static Integer getPort(String url) {

    int schemeEndIndex = url.indexOf(':');
    if (schemeEndIndex == -1) {
      // not a valid url
      return null;
    }

    int len = url.length();
    if (len <= schemeEndIndex + 2
        || url.charAt(schemeEndIndex + 1) != '/'
        || url.charAt(schemeEndIndex + 2) != '/') {
      // has no authority component
      return null;
    }

    // look for the end of the host:
    //   ':' ==> start of port, or
    //   '/', '?', '#' ==> start of path
    int index;
    int portIndex = -1;
    for (index = schemeEndIndex + 3; index < len; index++) {
      char c = url.charAt(index);
      if (c == ':') {
        portIndex = index + 1;
        break;
      }
      if (c == '/' || c == '?' || c == '#') {
        break;
      }
    }
    if (portIndex == -1) {
      String scheme = url.substring(0, schemeEndIndex);
      return getDefaultPortForScheme(scheme);
    }

    // look for the end of the port:
    //   '/', '?', '#' ==> start of path
    for (index = portIndex; index < len; index++) {
      char c = url.charAt(index);
      if (c == '/' || c == '?' || c == '#') {
        break;
      }
    }
    String port = url.substring(portIndex, index);
    return port.isEmpty() ? null : safeParse(port);
  }

  @Nullable
  private static Integer safeParse(String port) {
    try {
      return Integer.valueOf(port);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer getDefaultPortForScheme(String scheme) {
    if (scheme.equals("https")) {
      return 443;
    }
    if (scheme.equals("http")) {
      return 80;
    }
    return null;
  }

  private UrlParser() {}
}
