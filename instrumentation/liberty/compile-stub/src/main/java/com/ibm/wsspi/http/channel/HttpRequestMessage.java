/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.wsspi.http.channel;

import com.ibm.wsspi.genericbnf.HeaderField;
import java.util.List;

// https://github.com/OpenLiberty/open-liberty/blob/master/dev/com.ibm.ws.transport.http/src/com/ibm/wsspi/http/channel/HttpRequestMessage.java
public interface HttpRequestMessage {

  String getMethod();

  HeaderField getHeader(String paramString);

  List<HeaderField> getHeaders(String name);

  String getScheme();

  String getRequestURI();

  String getQueryString();

  String getVersion();

  List<String> getAllHeaderNames();
}
