/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.armeria.grpc.v1_14;

import java.net.URI;
import javax.annotation.Nullable;

final class ArmeriaGrpcTarget {

  @Nullable
  static String fromUri(@Nullable URI uri) {
    if (uri == null) {
      return null;
    }
    String authority = uri.getRawAuthority();
    if (authority == null) {
      return null;
    }
    int userInfoEnd = authority.lastIndexOf('@');
    if (userInfoEnd >= 0) {
      authority = authority.substring(userInfoEnd + 1);
    }
    authority = authority.replace("[", "%5B").replace("]", "%5D");
    return "dns:///" + authority;
  }

  private ArmeriaGrpcTarget() {}
}
