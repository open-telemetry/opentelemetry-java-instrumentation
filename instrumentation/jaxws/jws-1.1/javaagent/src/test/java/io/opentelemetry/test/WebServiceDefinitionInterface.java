/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import javax.jws.WebService;

/**
 * Note: this has to stay outside of 'io.opentelemetry.javaagent' package to be considered for
 * instrumentation
 */
@WebService
public interface WebServiceDefinitionInterface {
  void partOfPublicInterface();
}
