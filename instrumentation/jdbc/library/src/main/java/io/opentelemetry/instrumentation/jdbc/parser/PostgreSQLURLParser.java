/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc.parser;

import io.opentelemetry.instrumentation.jdbc.ConnectionInfo;
import java.net.URI;
import java.net.URISyntaxException;

public class PostgreSQLURLParser extends AbstractURLParser {

  private static final int DEFAULT_PORT = 5432;
  private static final String DB_TYPE = "postgresql";

  @Override
  protected URLLocation fetchDatabaseHostsIndexRange(String url) {
    int hostLabelStartIndex = url.indexOf("//");
    int hostLabelEndIndex = url.indexOf("/", hostLabelStartIndex + 2);
    return new URLLocation(hostLabelStartIndex + 2, hostLabelEndIndex);
  }

  @Override
  protected URLLocation fetchDatabaseNameIndexRange(String url) {
    int hostLabelStartIndex = url.indexOf("//");
    int hostLabelEndIndex = url.indexOf("/", hostLabelStartIndex + 2);
    int databaseStartTag = url.indexOf("/", hostLabelEndIndex);
    int databaseEndTag = url.indexOf("?", databaseStartTag);
    if (databaseEndTag == -1) {
      databaseEndTag = url.length();
    }
    return new URLLocation(databaseStartTag + 1, databaseEndTag);
  }

  @Override
  public ConnectionInfo parse(String url) {
    URLLocation location = fetchDatabaseHostsIndexRange(url);
    String hosts = url.substring(location.startIndex(), location.endIndex());
    String[] hostSegment = hosts.split(",");
    if (hostSegment.length > 1) {
      StringBuilder sb = new StringBuilder();
      for (String host : hostSegment) {
        URI uri = parseHost(host);
        int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();

        sb.append(uri.getHost() + ":" + port + ",");
      }
      if (',' == sb.charAt(sb.length() - 1)) {
        sb.deleteCharAt(sb.length() - 1);
      }
      return new ConnectionInfo.Builder(sb.toString())
          .dbType(DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url))
          .build();
    } else {
      URI uri = parseHost(hostSegment[0]);
      int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();

      return new ConnectionInfo.Builder(uri.getHost(), port)
          .dbType(DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url))
          .build();
    }
  }

  private URI parseHost(String host) {
    try {
      return new URI("proto://" + host);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
  }
}
