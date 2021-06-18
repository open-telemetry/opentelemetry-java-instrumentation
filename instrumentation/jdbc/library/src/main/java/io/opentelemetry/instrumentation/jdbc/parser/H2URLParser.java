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

public class H2URLParser extends AbstractURLParser {

  private static final String LOCALHOST = "localhost";
  private static final int DEFAULT_PORT = 8084;
  /** Flag that H2 running with memory mode. */
  private static final String MEMORY_MODE_FLAG = "mem";
  /** Flag that H2 running with tcp mode. */
  private static final String TCP_MODE_FLAG = "h2:tcp";
  /** Flag that H2 running with file mode. */
  private static final String FILE_MODE_FLAG = "file";
  /** Flag that H2 running with implicit file mode. */
  private static final String IMPLICIT_FILE_MODE_FLAG = "jdbc:h2";

  private static final String H2_DB_TYPE = "h2";

  @Override
  protected URLLocation fetchDatabaseHostsIndexRange(String url) {
    int hostLabelStartIndex = url.indexOf("//");
    int hostLabelEndIndex = url.indexOf("/", hostLabelStartIndex + 2);
    return new URLLocation(hostLabelStartIndex + 2, hostLabelEndIndex);
  }

  @Override
  protected URLLocation fetchDatabaseNameIndexRange(String url) {
    int databaseStartTag = url.lastIndexOf("/");
    int databaseEndTag = url.indexOf(";");
    if (databaseEndTag == -1) {
      databaseEndTag = url.length();
    }
    return new URLLocation(databaseStartTag + 1, databaseEndTag);
  }

  @Override
  public ConnectionInfo parse(String url) {
    int[] databaseNameRangeIndex = fetchDatabaseNameRangeIndexFromURLForH2FileMode(url);
    if (databaseNameRangeIndex != null) {
      return new ConnectionInfo.Builder(LOCALHOST, -1)
          .dbType(H2_DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url, databaseNameRangeIndex))
          .build();
    }

    databaseNameRangeIndex = fetchDatabaseNameRangeIndexFromURLForH2MemMode(url);
    if (databaseNameRangeIndex != null) {
      return new ConnectionInfo.Builder(LOCALHOST, -1)
          .dbType(H2_DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url, databaseNameRangeIndex))
          .build();
    }

    databaseNameRangeIndex = fetchDatabaseNameRangeIndexFromURLForH2ImplicitFileMode(url);
    if (databaseNameRangeIndex != null) {
      return new ConnectionInfo.Builder(LOCALHOST, -1)
          .dbType(H2_DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url, databaseNameRangeIndex))
          .build();
    }

    String[] hostAndPort = fetchDatabaseHostsFromURL(url).split(":");
    if (hostAndPort.length == 1) {
      return new ConnectionInfo.Builder(hostAndPort[0], DEFAULT_PORT)
          .dbType(H2_DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url))
          .build();
    } else {
      return new ConnectionInfo.Builder(hostAndPort[0], Integer.valueOf(hostAndPort[1]))
          .dbType(H2_DB_TYPE)
          .dbInstance(fetchDatabaseNameFromURL(url))
          .build();
    }
  }

  /**
   * Fetch range index that the database name from connection url if H2 database running with file
   * mode.
   *
   * @return range index that the database name.
   */
  private int[] fetchDatabaseNameRangeIndexFromURLForH2FileMode(String url) {
    int fileLabelIndex = url.indexOf(FILE_MODE_FLAG);
    int parameterLabelIndex = url.indexOf(";", fileLabelIndex);
    if (parameterLabelIndex == -1) {
      parameterLabelIndex = url.length();
    }

    if (fileLabelIndex != -1) {
      return new int[] {fileLabelIndex + FILE_MODE_FLAG.length() + 1, parameterLabelIndex};
    } else {
      return null;
    }
  }

  /**
   * Fetch range index that the database name from connection url if H2 database running with
   * implicit file mode.
   *
   * @return range index that the database name.
   */
  private int[] fetchDatabaseNameRangeIndexFromURLForH2ImplicitFileMode(String url) {
    if (url.contains(TCP_MODE_FLAG)) {
      return null;
    }
    int fileLabelIndex = url.indexOf(IMPLICIT_FILE_MODE_FLAG);
    int parameterLabelIndex = url.indexOf(";", fileLabelIndex);
    if (parameterLabelIndex == -1) {
      parameterLabelIndex = url.length();
    }

    if (fileLabelIndex != -1) {
      return new int[] {fileLabelIndex + IMPLICIT_FILE_MODE_FLAG.length() + 1, parameterLabelIndex};
    } else {
      return null;
    }
  }

  /**
   * Fetch range index that the database name from connection url if H2 database running with memory
   * mode.
   *
   * @return range index that the database name.
   */
  private int[] fetchDatabaseNameRangeIndexFromURLForH2MemMode(String url) {
    int fileLabelIndex = url.indexOf(MEMORY_MODE_FLAG);
    int parameterLabelIndex = url.indexOf(";", fileLabelIndex);
    if (parameterLabelIndex == -1) {
      parameterLabelIndex = url.length();
    }

    if (fileLabelIndex != -1) {
      return new int[] {fileLabelIndex + MEMORY_MODE_FLAG.length() + 1, parameterLabelIndex};
    } else {
      return null;
    }
  }
}
