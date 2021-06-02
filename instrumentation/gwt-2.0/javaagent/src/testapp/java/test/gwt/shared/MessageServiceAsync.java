/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.gwt.shared;

import com.google.gwt.user.client.rpc.AsyncCallback;
import java.io.IOException;

/** The async counterpart of <code>MessageService</code>. */
public interface MessageServiceAsync {
  void sendMessage(String input, AsyncCallback<String> callback) throws IOException;
}
