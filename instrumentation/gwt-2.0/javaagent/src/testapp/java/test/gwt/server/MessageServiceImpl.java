/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.gwt.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import test.gwt.shared.MessageService;

/** The server-side implementation of the RPC service. */
@SuppressWarnings("serial")
public class MessageServiceImpl extends RemoteServiceServlet implements MessageService {

  @Override
  public String sendMessage(String message) throws IllegalArgumentException {
    if (message == null || "Error".equals(message)) {
      throw new IllegalArgumentException();
    }

    return "Hello, " + message;
  }
}
