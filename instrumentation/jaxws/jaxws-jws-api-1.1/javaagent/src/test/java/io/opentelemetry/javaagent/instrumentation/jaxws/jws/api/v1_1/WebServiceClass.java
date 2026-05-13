/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.api.v1_1;

import javax.jws.WebService;

@WebService
class WebServiceClass {
  public void doSomethingPublic() {}

  protected void doSomethingProtected() {}

  void doSomethingPackagePrivate() {}
}
