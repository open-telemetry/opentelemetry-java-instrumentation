/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url.internal;

import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class UrlSanitizer {

  private static final String REDACTED = "REDACTED";

  /**
   * Removes the credentials that the semantic conventions require to be absent from {@code
   * url.full}: the {@code userinfo} component of the authority, and the values of any query
   * parameter named in {@code sensitiveQueryParameters}.
   */
  @Nullable
  public static String sanitizeUrl(@Nullable String url, Set<String> sensitiveQueryParameters) {
    if (url == null || url.isEmpty()) {
      return url;
    }

    url = redactUserInfo(url);
    url = UrlQuerySanitizer.redactUrl(url, sensitiveQueryParameters);

    return url;
  }

  private static String redactUserInfo(String url) {
    int schemeEndIndex = url.indexOf(':');

    if (schemeEndIndex == -1) {
      // not a valid url
      return url;
    }

    int len = url.length();
    if (len <= schemeEndIndex + 2
        || url.charAt(schemeEndIndex + 1) != '/'
        || url.charAt(schemeEndIndex + 2) != '/') {
      // has no authority component
      return url;
    }

    // look for the end of the authority component:
    //   '/', '?', '#' ==> start of path
    int index;
    int atIndex = -1;
    for (index = schemeEndIndex + 3; index < len; index++) {
      char c = url.charAt(index);

      if (c == '@') {
        atIndex = index;
      }

      if (c == '/' || c == '?' || c == '#') {
        break;
      }
    }

    if (atIndex == -1 || atIndex == len - 1) {
      return url;
    }
    return url.substring(0, schemeEndIndex + 3)
        + REDACTED
        + ":"
        + REDACTED
        + url.substring(atIndex);
  }

  private UrlSanitizer() {}
}
