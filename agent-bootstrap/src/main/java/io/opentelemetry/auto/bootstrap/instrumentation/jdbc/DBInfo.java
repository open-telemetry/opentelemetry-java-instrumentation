/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.bootstrap.instrumentation.jdbc;

import java.util.Objects;

public class DBInfo {

  public static final DBInfo DEFAULT = new Builder().build();

  private final String type;
  private final String subtype;
  private final String shortUrl; // "type:[subtype:]//host:port"
  private final String user;
  private final String instance;
  private final String db;
  private final String host;
  private final Integer port;

  public DBInfo(String type, String subtype, String shortUrl, String user, String instance, String db, String host,
                Integer port) {
    this.type = type;
    this.subtype = subtype;
    this.shortUrl = shortUrl;
    this.user = user;
    this.instance = instance;
    this.db = db;
    this.host = host;
    this.port = port;
  }

  public String getType() {
    return type;
  }

  public String getSubtype() {
    return subtype;
  }

  public String getShortUrl() {
    return shortUrl;
  }

  public String getUser() {
    return user;
  }

  public String getInstance() {
    return instance;
  }

  public String getDb() {
    return db;
  }

  public String getHost() {
    return host;
  }

  public Integer getPort() {
    return port;
  }

  public Builder toBuilder() {
    return new Builder()
            .type(type)
            .subtype(subtype)
            .shortUrl(shortUrl)
            .user(user)
            .instance(instance)
            .db(db)
            .host(host)
            .port(port);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DBInfo dbInfo = (DBInfo) o;
    return Objects.equals(type, dbInfo.type) &&
            Objects.equals(subtype, dbInfo.subtype) &&
            Objects.equals(shortUrl, dbInfo.shortUrl) &&
            Objects.equals(user, dbInfo.user) &&
            Objects.equals(instance, dbInfo.instance) &&
            Objects.equals(db, dbInfo.db) &&
            Objects.equals(host, dbInfo.host) &&
            Objects.equals(port, dbInfo.port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, subtype, shortUrl, user, instance, db, host, port);
  }

  public static class Builder {
    private String type;
    private String subtype;
    private String shortUrl;
    private String user;
    private String instance;
    private String db;
    private String host;
    private Integer port;

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder subtype(String subtype) {
      this.subtype = subtype;
      return this;
    }

    public Builder shortUrl(String shortUrl) {
      this.shortUrl = shortUrl;
      return this;
    }

    public Builder user(String user) {
      this.user = user;
      return this;
    }

    public Builder instance(String instance) {
      this.instance = instance;
      return this;
    }

    public Builder db(String db) {
      this.db = db;
      return this;
    }

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder port(Integer port) {
      this.port = port;
      return this;
    }

    public DBInfo build() {
      return new DBInfo(type, subtype, shortUrl, user, instance, db, host, port);
    }
  }

}
