/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import javax.annotation.Nullable;

class UrlParser {

  @Nullable
  static String getHost(String url) {

    int startIndex = getHostStartIndex(url);
    if (startIndex == -1) {
      return null;
    }

    int endIndexExclusive = getHostEndIndexExclusive(url, startIndex);
    if (endIndexExclusive == startIndex) {
      return null;
    }

    return url.substring(startIndex, endIndexExclusive);
  }

  @Nullable
  static Integer getPort(String url) {

    int hostStartIndex = getHostStartIndex(url);
    if (hostStartIndex == -1) {
      return null;
    }

    int hostEndIndexExclusive = getHostEndIndexExclusive(url, hostStartIndex);
    if (hostEndIndexExclusive == hostStartIndex) {
      return null;
    }

    if (hostEndIndexExclusive < url.length() && url.charAt(hostEndIndexExclusive) != ':') {
      return null;
    }

    int portStartIndex = hostEndIndexExclusive + 1;

    int portEndIndexExclusive = getPortEndIndexExclusive(url, portStartIndex);
    if (portEndIndexExclusive == portStartIndex) {
      return null;
    }

    return safeParse(url.substring(portStartIndex, portEndIndexExclusive));
  }

  private static int getHostStartIndex(String url) {

    int schemeEndIndex = url.indexOf(':');
    if (schemeEndIndex == -1) {
      // not a valid url
      return -1;
    }

    int len = url.length();
    if (len <= schemeEndIndex + 2
        || url.charAt(schemeEndIndex + 1) != '/'
        || url.charAt(schemeEndIndex + 2) != '/') {
      // has no authority component
      return -1;
    }

    return schemeEndIndex + 3;
  }

  private static int getHostEndIndexExclusive(String url, int startIndex) {
    // look for the end of the host:
    //   ':' ==> start of port, or
    //   '/', '?', '#' ==> start of path
    int index;
    int len = url.length();
    for (index = startIndex; index < len; index++) {
      char c = url.charAt(index);
      if (c == ':' || c == '/' || c == '?' || c == '#') {
        break;
      }
    }
    return index;
  }

  private static int getPortEndIndexExclusive(String url, int startIndex) {
    // look for the end of the port:
    //   '/', '?', '#' ==> start of path
    int index;
    int len = url.length();
    for (index = startIndex; index < len; index++) {
      char c = url.charAt(index);
      if (c == '/' || c == '?' || c == '#') {
        break;
      }
    }
    return index;
  }

  @Nullable
  private static Integer safeParse(String port) {
    try {
      return Integer.valueOf(port);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private UrlParser() {}
}
