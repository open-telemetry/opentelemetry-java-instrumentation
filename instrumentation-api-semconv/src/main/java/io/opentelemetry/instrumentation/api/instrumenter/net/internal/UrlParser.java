/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.net.internal;

import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class UrlParser {

  @Nullable
  public static String getHost(String url) {

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
  public static Integer getPort(String url) {

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

  @Nullable
  public static String getPath(String url) {

    int hostStartIndex = getHostStartIndex(url);
    if (hostStartIndex == -1) {
      return null;
    }

    int hostEndIndexExclusive = getHostEndIndexExclusive(url, hostStartIndex);
    if (hostEndIndexExclusive == hostStartIndex) {
      return null;
    }

    int pathStartIndex = url.indexOf('/', hostEndIndexExclusive);
    if (pathStartIndex == -1) {
      return null;
    }

    int pathEndIndexExclusive = getPathEndIndexExclusive(url, pathStartIndex);
    if (pathEndIndexExclusive == pathStartIndex) {
      return null;
    }

    return url.substring(pathStartIndex, pathEndIndexExclusive);
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
    return getEndIndexExclusive(
        url, startIndex, c -> (c == ':' || c == '/' || c == '?' || c == '#'));
  }

  private static int getPortEndIndexExclusive(String url, int startIndex) {
    // look for the end of the port:
    //   '/', '?', '#' ==> start of path
    return getEndIndexExclusive(url, startIndex, c -> (c == '/' || c == '?' || c == '#'));
  }

  private static int getPathEndIndexExclusive(String url, int startIndex) {
    // look for the end of the path:
    //   '?', '#' ==> end of path
    return getEndIndexExclusive(url, startIndex, c -> (c == '?' || c == '#'));
  }

  private static int getEndIndexExclusive(
      String url, int startIndex, Predicate<Character> predicate) {
    int index;
    int len = url.length();
    for (index = startIndex; index < len; index++) {
      char c = url.charAt(index);
      if (predicate.test(c)) {
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
