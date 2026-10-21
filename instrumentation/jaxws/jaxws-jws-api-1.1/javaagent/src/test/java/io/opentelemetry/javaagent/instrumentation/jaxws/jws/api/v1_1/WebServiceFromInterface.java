/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.api.v1_1;

class WebServiceFromInterface implements WebServiceDefinitionInterface {
  @Override
  public void partOfPublicInterface() {}

  @Override
  public String covariantMethod() {
    return "";
  }

  public void notPartOfPublicInterface() {}

  void notPartOfAnything() {}
}
