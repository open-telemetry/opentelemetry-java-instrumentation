/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import javax.jws.WebService;

// This is pure java to not have any groovy generated public method surprises
@WebService
public class WebServiceClass {
  public void doSomethingPublic() {}

  protected void doSomethingProtected() {}

  void doSomethingPackagePrivate() {}
}
