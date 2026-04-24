/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1;

import javax.jws.WebService;

@WebService
public interface WebServiceDefinitionInterface {
  void partOfPublicInterface();
}
