/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.elasticsearch.client;

import java.lang.invoke.MethodHandles;

public final class RestClientPackageAccess {

  public static MethodHandles.Lookup getLookup() {
    return MethodHandles.lookup();
  }

  private RestClientPackageAccess() {}
}
