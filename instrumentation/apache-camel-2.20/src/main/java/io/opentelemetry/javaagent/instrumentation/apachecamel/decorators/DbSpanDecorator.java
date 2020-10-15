/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel.decorators;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.attributes.SemanticAttributes;
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
  public String getOperationName(Exchange exchange, Endpoint endpoint) {

    switch (component) {
      case "mongodb":
      case "elasticsearch":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          if (queryParameters.containsKey("operation")) {
            return queryParameters.get("operation");
          }
        }
    }
    return super.getOperationName(exchange, endpoint);
  }

  private String getStatement(Exchange exchange, Endpoint endpoint) {
    switch (component) {
      case "mongodb":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          return queryParameters.get("database");
        }
      case "cql":
        {
          Object cql = exchange.getIn().getHeader("CamelCqlQuery");
          if (cql != null) {
            return cql.toString();
          } else {
            Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
            if (queryParameters.containsKey("cql")) {
              return queryParameters.get("cql");
            }
          }
        }
      case "jdbc":
        {
          Object body = exchange.getIn().getBody();
          if (body instanceof String) {
            return (String) body;
          }
        }
      case "sql":
        {
          Object sqlquery = exchange.getIn().getHeader("CamelSqlQuery");
          if (sqlquery instanceof String) {
            return (String) sqlquery;
          }
        }
    }
    return null;
  }

  private String getDbName(Endpoint endpoint) {
    switch (component) {
      case "mongodb":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          return queryParameters.toString();
        }
      case "cql":
        {
          URI uri = URI.create(endpoint.getEndpointUri());
          if (uri.getPath() != null && uri.getPath().length() > 0) {
            // Strip leading '/' from path
            return uri.getPath().substring(1);
          }
        }
      case "elasticsearch":
        {
          Map<String, String> queryParameters = toQueryParameters(endpoint.getEndpointUri());
          if (queryParameters.containsKey("indexName")) {
            return queryParameters.get("indexName");
          }
        }
    }
    return null;
  }

  @Override
  public void pre(Span span, Exchange exchange, Endpoint endpoint) {
    super.pre(span, exchange, endpoint);

    span.setAttribute(SemanticAttributes.DB_SYSTEM, system);
    String statement = getStatement(exchange, endpoint);
    if (statement != null) {
      span.setAttribute(SemanticAttributes.DB_STATEMENT, statement);
    }
    String dbName = getDbName(endpoint);
    if (dbName != null) {
      span.setAttribute(SemanticAttributes.DB_NAME, dbName);
    }
  }
}
