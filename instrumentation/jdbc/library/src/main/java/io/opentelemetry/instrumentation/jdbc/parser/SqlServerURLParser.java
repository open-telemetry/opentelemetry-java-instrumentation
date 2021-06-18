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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class SqlServerURLParser implements ConnectionURLParser {

  private static final int DEFAULT_PORT = 1433;

  protected String dbType() {
    return "sqlserver";
  }

  @Override
  public ConnectionInfo parse(String url) {
    String serverName = "";
    Integer port = DEFAULT_PORT;
    String dbInstance = null;
    int hostIndex = url.indexOf("://");
    if (hostIndex <= 0) {
      return null;
    }

    String[] split = url.split(";", 2);
    if (split.length > 1) {
      Map<String, String> props = parseQueryParams(split[1], ";");
      serverName = props.get("serverName");
      dbInstance = props.get("databaseName");
      if (props.containsKey("portNumber")) {
        String portNumber = props.get("portNumber");
        try {
          port = Integer.parseInt(portNumber);
        } catch (NumberFormatException ignored) {
          // nothing to do, expected
        }
      }
    }

    String urlServerName = split[0].substring(hostIndex + 3);
    if (!urlServerName.isEmpty()) {
      serverName = urlServerName;
    }

    int portLoc = serverName.indexOf(":");
    if (portLoc > 1) {
      port = Integer.parseInt(serverName.substring(portLoc + 1));
      serverName = serverName.substring(0, portLoc);
    }

    int instanceLoc = serverName.indexOf("\\");
    if (instanceLoc > 1) {
      serverName = serverName.substring(0, instanceLoc);
    }

    if (serverName.isEmpty()) {
      return null;
    }

    return new ConnectionInfo.Builder(serverName, port)
        .dbType(dbType())
        .dbInstance(dbInstance)
        .build();
  }

  private static Map<String, String> parseQueryParams(String query, String separator) {
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> queryParams = new LinkedHashMap<>();
    String[] pairs = query.split(separator);
    for (String pair : pairs) {
      try {
        int idx = pair.indexOf("=");
        String key =
            idx > 0
                ? URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name())
                : pair;
        if (!queryParams.containsKey(key)) {
          String value =
              idx > 0 && pair.length() > idx + 1
                  ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
                  : null;
          queryParams.put(key, value);
        }
      } catch (UnsupportedEncodingException e) {
        // Ignore.
      }
    }
    return queryParams;
  }
}
