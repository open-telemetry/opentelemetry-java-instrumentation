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

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.instrumentation.apachecamel.CamelDirection;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;

class DbSpanDecorator extends BaseSpanDecorator {

  private final String component;
  private final String system;

  DbSpanDecorator(String component, String system) {
    this.component = component;
    this.system = system;
  }

  @Override
  public String getOperationName(
      Exchange exchange, Endpoint endpoint, CamelDirection camelDirection) {

    switch (component) {
      case "mongodb":
      case "elasticsearch":
        Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
        if (queryParameters.containsKey("operation")) {
          return queryParameters.get("operation");
        }
        return super.getOperationName(exchange, endpoint, camelDirection);
      default:
        return super.getOperationName(exchange, endpoint, camelDirection);
    }
  }

  // visible for testing
  String getStatement(Exchange exchange, Endpoint endpoint) {
    switch (component) {
      case "cql":
        Object cqlObj = exchange.getIn().getHeader("CamelCqlQuery");
        if (cqlObj != null) {
          return SqlStatementSanitizer.sanitize(cqlObj.toString()).getFullStatement();
        }
        return null;
      case "jdbc":
        Object body = exchange.getIn().getBody();
        if (body instanceof String) {
          return SqlStatementSanitizer.sanitize((String) body).getFullStatement();
        }
        return null;
      case "sql":
        Object sqlquery = exchange.getIn().getHeader("CamelSqlQuery");
        if (sqlquery instanceof String) {
          return SqlStatementSanitizer.sanitize((String) sqlquery).getFullStatement();
        }
        return null;
      default:
        return null;
    }
  }

  private String getDbName(Endpoint endpoint) {
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
        Map<String, String> elasticsearchParameters = toQueryParameters(endpoint.getEndpointUri());
        if (elasticsearchParameters.containsKey("indexName")) {
          return elasticsearchParameters.get("indexName");
        }
        return null;
      default:
        return null;
    }
  }

  @Override
  public void pre(
      AttributesBuilder attributes,
      Exchange exchange,
      Endpoint endpoint,
      CamelDirection camelDirection) {
    super.pre(attributes, exchange, endpoint, camelDirection);

    attributes.put(SemanticAttributes.DB_SYSTEM, system);
    String statement = getStatement(exchange, endpoint);
    if (statement != null) {
      attributes.put(SemanticAttributes.DB_STATEMENT, statement);
    }
    String dbName = getDbName(endpoint);
    if (dbName != null) {
      attributes.put(SemanticAttributes.DB_NAME, dbName);
    }
  }
}
