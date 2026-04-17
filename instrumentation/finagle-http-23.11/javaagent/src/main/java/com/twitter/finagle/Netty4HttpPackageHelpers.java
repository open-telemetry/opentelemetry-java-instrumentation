package com.twitter.finagle;

import com.twitter.finagle.netty4.http.package$;

public class Netty4HttpPackageHelpers {
  private Netty4HttpPackageHelpers() {}

  public static String getHttpCodecName() {
    return package$.MODULE$.HttpCodecName();
  }
}
