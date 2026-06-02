/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.twitter.finagle;

import com.twitter.finagle.netty4.http.package$;

public class Netty4HttpPackageHelpers {
  private Netty4HttpPackageHelpers() {}

  public static String getHttpCodecName() {
    return package$.MODULE$.HttpCodecName();
  }
}
