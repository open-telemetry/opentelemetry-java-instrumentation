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

public abstract class AbstractURLParser implements ConnectionURLParser {

  /**
   * Fetch the index range that database host and port from connection url.
   *
   * @return index range that database hosts.
   */
  protected abstract URLLocation fetchDatabaseHostsIndexRange(final String url);

  /**
   * Fetch the index range that database name from connection url.
   *
   * @return index range that database name.
   */
  protected abstract URLLocation fetchDatabaseNameIndexRange(final String url);

  /**
   * Fetch database host(s) from connection url.
   *
   * @return database host(s).
   */
  protected String fetchDatabaseHostsFromURL(String url) {
    URLLocation hostsLocation = fetchDatabaseHostsIndexRange(url);
    return url.substring(hostsLocation.startIndex(), hostsLocation.endIndex());
  }

  /**
   * Fetch database name from connection url.
   *
   * @return database name.
   */
  protected String fetchDatabaseNameFromURL(String url) {
    URLLocation hostsLocation = fetchDatabaseNameIndexRange(url);
    return url.substring(hostsLocation.startIndex(), hostsLocation.endIndex());
  }

  /**
   * Fetch database name from connection url.
   *
   * @return database name.
   */
  protected String fetchDatabaseNameFromURL(String url, int[] indexRange) {
    return url.substring(indexRange[0], indexRange[1]);
  }

}
