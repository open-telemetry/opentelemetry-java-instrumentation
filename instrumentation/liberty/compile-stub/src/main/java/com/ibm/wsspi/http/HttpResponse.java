/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.wsspi.http;

import java.util.List;

// https://github.com/OpenLiberty/open-liberty/blob/integration/dev/com.ibm.ws.transport.http/src/com/ibm/wsspi/http/HttpResponse.java
public interface HttpResponse {

  List<String> getHeaders(String name);
}
