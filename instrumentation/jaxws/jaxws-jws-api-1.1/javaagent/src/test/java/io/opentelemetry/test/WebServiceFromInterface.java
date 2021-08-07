/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

/**
 * Note: this has to stay outside of 'io.opentelemetry.javaagent' package to be considered for
 * instrumentation
 */
public class WebServiceFromInterface implements WebServiceDefinitionInterface {
  @Override
  public void partOfPublicInterface() {}

  public void notPartOfPublicInterface() {}

  void notPartOfAnything() {}
}
