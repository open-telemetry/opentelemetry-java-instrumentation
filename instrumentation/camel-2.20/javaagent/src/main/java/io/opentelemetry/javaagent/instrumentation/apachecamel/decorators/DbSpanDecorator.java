/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlDialect.DOUBLE_QUOTES_ARE_STRING_LITERALS;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQuery;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlQueryAnalyzer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import java.net.URI;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class DbSpanDecorator extends BaseSpanDecorator {

  private static final SqlQueryAnalyzer analyzer =
      SqlQueryAnalyzer.create(AgentCommonConfig.get().isQuerySanitizationEnabled());

  private final String component;
  private final String system;

  DbSpanDecorator(String component, String system) {
    this.component = component;
    this.system = system;
  }

  @Override
  public boolean shouldStartNewSpan() {
    // Under stable database semconv, don't create Camel DB spans. The underlying database
    // instrumentations (JDBC, Cassandra, MongoDB, etc.) produce more accurate spans with correct
    // db.system.name, connection attributes, and dialect-aware query sanitization.
    return !emitStableDatabaseSemconv();
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {

    switch (component) {
      case "mongodb":
      case "elasticsearch":
      case "opensearch":
        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        if (queryParameters.containsKey("operation")) {
          return queryParameters.get("operation");
        }
        return super.getOperationName(exchange, endpoint, camelDirection);
      default:
        return super.getOperationName(exchange, endpoint, camelDirection);
    }
  }

  @Nullable
  private String getRawQueryText(Exchange exchange) {
    switch (component) {
      case "cql":
        Object cqlObj = exchange.getIn().getHeader("CamelCqlQuery");
        return cqlObj != null ? cqlObj.toString() : null;
      case "jdbc":
        Object body = exchange.getIn().getBody();
        return body instanceof String ? (String) body : null;
      case "sql":
        Object sqlquery = exchange.getIn().getHeader("CamelSqlQuery");
        return sqlquery instanceof String ? (String) sqlquery : null;
      default:
        return null;
    }
  }

  private String getDbNamespace(Endpoint endpoint) {
    switch (component) {
      case "mongodb":
        Map<String, String> mongoParameters = toQueryParameters(endpoint.getEndpointUri());
        return mongoParameters.get("database");
      case "cql":
        URI uri = URI.create(endpoint.getEndpointUri());
        if (uri.getPath() != null && uri.getPath().length() > 0) {
          // Strip leading '/' from path
          return uri.getPath().substring(1);
        }
        return null;
      case "elasticsearch":
      case "opensearch":
        Map<String, String> elasticsearchParameters = toQueryParameters(endpoint.getEndpointUri());
        if (elasticsearchParameters.containsKey("indexName")) {
          return elasticsearchParameters.get("indexName");
        }
        return null;
      default:
        return null;
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  @Override
  public void pre(
      AttributesBuilder attributes,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection) {
    super.pre(attributes, exchange, endpoint, camelDirection);

    if (emitStableDatabaseSemconv()) {
      attributes.put(DB_SYSTEM_NAME, system);
    }
    if (emitOldDatabaseSemconv()) {
      attributes.put(DB_SYSTEM, system);
    }

    setQueryAttributes(attributes, exchange);

    String namespace = getDbNamespace(endpoint);
    if (namespace != null) {
      if (emitStableDatabaseSemconv()) {
        attributes.put(DB_NAMESPACE, namespace);
      }
      if (emitOldDatabaseSemconv()) {
        attributes.put(DB_NAME, namespace);
      }
    }
  }

  @SuppressWarnings("deprecation") // using deprecated semconv
  void setQueryAttributes(AttributesBuilder attributes, Exchange exchange) {
    String rawQueryText = getRawQueryText(exchange);
    if (rawQueryText != null) {
      // using the conservative default since the underlying database is unknown to camel
      // TODO consider removing db spans from camel since underlying database instrumentation
      // can do a better job
      SqlQuery sqlQuery =
          emitOldDatabaseSemconv()
              ? analyzer.analyze(rawQueryText, DOUBLE_QUOTES_ARE_STRING_LITERALS)
              : null;
      SqlQuery sqlQueryWithSummary =
          emitStableDatabaseSemconv()
              ? analyzer.analyzeWithSummary(rawQueryText, DOUBLE_QUOTES_ARE_STRING_LITERALS)
              : null;

      if (sqlQueryWithSummary != null) {
        attributes.put(DB_QUERY_TEXT, sqlQueryWithSummary.getQueryText());
        attributes.put(DB_QUERY_SUMMARY, sqlQueryWithSummary.getQuerySummary());
      }
      if (sqlQuery != null) {
        attributes.put(DB_STATEMENT, sqlQuery.getQueryText());
      }
    }
  }
}
