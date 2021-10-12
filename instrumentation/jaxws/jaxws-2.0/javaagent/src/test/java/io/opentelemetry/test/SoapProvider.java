/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.test;

import javax.xml.ws.Provider;

public class SoapProvider implements Provider<SoapProvider.Message> {

  @Override
  public Message invoke(Message message) {
    return message;
  }

  public static class Message {}
}
