/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import javax.jws.WebService;

@WebService
public interface WebServiceDefinitionInterface {
  void partOfPublicInterface();
}
