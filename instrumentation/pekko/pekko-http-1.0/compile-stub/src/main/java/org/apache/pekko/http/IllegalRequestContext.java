/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.apache.pekko.http;

import java.util.Optional;
import org.apache.pekko.http.javadsl.model.HttpMethod;
import org.apache.pekko.http.javadsl.model.HttpProtocol;

// stub of the class that pekko-http 2.0 added, the javaagent compiles against pekko-http 1.0; this
// class is consumed as a compileOnly module and replaced at runtime by the pekko-http 2.0 class
public final class IllegalRequestContext {

  private IllegalRequestContext() {}

  public Optional<HttpMethod> getMethod() {
    throw new UnsupportedOperationException();
  }

  public Optional<String> getRawRequestTarget() {
    throw new UnsupportedOperationException();
  }

  public Optional<HttpProtocol> getProtocol() {
    throw new UnsupportedOperationException();
  }
}
