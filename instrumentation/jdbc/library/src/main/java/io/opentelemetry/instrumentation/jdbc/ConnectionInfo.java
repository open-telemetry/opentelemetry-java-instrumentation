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

package io.opentelemetry.instrumentation.jdbc;

public class ConnectionInfo {

  public static final ConnectionInfo UNKNOWN_CONNECTION_INFO =
      new Builder("unknown_peer").dbType("unknown_type").dbInstance("unknown_instance").build();

  private final String dbType;
  private final String dbUser;
  private final String dbInstance;
  private final String dbPeer;
  private final String dbPeerService;

  private ConnectionInfo(
      String dbType, String dbUser, String dbInstance, String dbHost, Integer dbPort) {
    this.dbType = dbType;
    this.dbUser = dbUser;
    this.dbInstance = dbInstance;
    if (dbHost != null && dbPort != null) {
      this.dbPeer = dbHost + ":" + dbPort;
    } else {
      this.dbPeer = "";
    }

    this.dbPeerService = makePeerService();
  }

  private ConnectionInfo(String dbType, String dbUser, String dbInstance, String dbPeer) {
    this.dbType = dbType;
    this.dbUser = dbUser;
    this.dbInstance = dbInstance;
    this.dbPeer = dbPeer;

    this.dbPeerService = makePeerService();
  }

  /** Make a unique serviceName that could be used in dependency diagram. */
  private String makePeerService() {
    if (null != dbInstance && !dbInstance.isEmpty()) {
      return dbInstance + "[" + dbType + "(" + dbPeer + ")]";
    } else {
      return dbType + "(" + dbPeer + ")";
    }
  }

  public String getDbType() {
    return dbType;
  }

  public String getDbUser() {
    return dbUser;
  }

  public String getDbInstance() {
    return dbInstance;
  }

  public String getDbPeer() {
    return dbPeer;
  }

  public String getPeerService() {
    return dbPeerService;
  }

  public static class Builder {

    private String dbType;
    private String dbUser;
    private String dbInstance;
    private String dbHost;
    private Integer dbPort;
    private String dbPeer;

    public Builder(String dbPeer) {
      this.dbPeer = dbPeer;
    }

    public Builder(String dbHost, Integer dbPort) {
      this.dbHost = dbHost;
      this.dbPort = dbPort;
    }

    public Builder dbType(String dbType) {
      this.dbType = dbType;
      return this;
    }

    public Builder dbUser(String dbUser) {
      this.dbUser = dbUser;
      return this;
    }

    public Builder dbInstance(String dbInstance) {
      this.dbInstance = dbInstance;
      return this;
    }

    public ConnectionInfo build() {
      if (this.dbPeer != null && !dbPeer.isEmpty()) {
        return new ConnectionInfo(this.dbType, this.dbUser, this.dbInstance, this.dbPeer);
      }
      return new ConnectionInfo(
          this.dbType, this.dbUser, this.dbInstance, this.dbHost, this.dbPort);
    }
  }
}
