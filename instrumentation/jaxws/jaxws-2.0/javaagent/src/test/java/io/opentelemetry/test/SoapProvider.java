/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import javax.xml.ws.Provider;

/**
 * Note: this has to stay outside of 'io.opentelemetry.javaagent' package to be considered for
 * instrumentation
 */
public class SoapProvider implements Provider<SoapProvider.Message> {

  @Override
  public Message invoke(Message message) {
    return message;
  }

  public static class Message {}
}
