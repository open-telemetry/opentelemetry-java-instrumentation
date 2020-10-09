/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.api.jdbc;

import java.util.Objects;

public class DBInfo {

  public static final DBInfo DEFAULT = new Builder().build();

  private final String system;
  private final String subtype;
  private final String shortUrl; // "type:[subtype:]//host:port"
  private final String user;
  private final String name;
  private final String db;
  private final String host;
  private final Integer port;

  public DBInfo(
      String system,
      String subtype,
      String shortUrl,
      String user,
      String name,
      String db,
      String host,
      Integer port) {
    this.system = system;
    this.subtype = subtype;
    this.shortUrl = shortUrl;
    this.user = user;
    this.name = name;
    this.db = db;
    this.host = host;
    this.port = port;
  }

  public String getSystem() {
    return system;
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

  public String getName() {
    return name;
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
        .system(system)
        .subtype(subtype)
        .shortUrl(shortUrl)
        .user(user)
        .name(name)
        .db(db)
        .host(host)
        .port(port);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DBInfo)) {
      return false;
    }
    DBInfo dbInfo = (DBInfo) o;
    return Objects.equals(system, dbInfo.system)
        && Objects.equals(subtype, dbInfo.subtype)
        && Objects.equals(shortUrl, dbInfo.shortUrl)
        && Objects.equals(user, dbInfo.user)
        && Objects.equals(name, dbInfo.name)
        && Objects.equals(db, dbInfo.db)
        && Objects.equals(host, dbInfo.host)
        && Objects.equals(port, dbInfo.port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(system, subtype, shortUrl, user, name, db, host, port);
  }

  public static class Builder {
    private String system;
    private String subtype;
    private String shortUrl;
    private String user;
    private String name;
    private String db;
    private String host;
    private Integer port;

    public Builder system(String system) {
      this.system = system;
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

    public Builder name(String name) {
      this.name = name;
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
      return new DBInfo(system, subtype, shortUrl, user, name, db, host, port);
    }
  }
}
