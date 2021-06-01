/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.gwt.shared;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;
import java.io.IOException;

/** The client-side stub for the RPC service. */
@RemoteServiceRelativePath("greet")
public interface MessageService extends RemoteService {
  String sendMessage(String message) throws IOException;
}
