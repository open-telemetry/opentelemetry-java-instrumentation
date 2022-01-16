package io.opentelemetry.instrumentation.graphql.v17;

import io.opentelemetry.api.common.AttributeKey;

final class GraphQLSingletons {

  // https://github.com/open-telemetry/opentelemetry-js-contrib/blob/main/plugins/node/opentelemetry-instrumentation-graphql/src/enums/AttributeNames.ts
  static final AttributeKey<String> COMPONENT = AttributeKey.stringKey("graphql");
  static final AttributeKey<String> GRAPHQL_SOURCE = AttributeKey.stringKey("graphql.source");
  static final AttributeKey<String> FIELD_NAME = AttributeKey.stringKey("graphql.field.name");
  static final AttributeKey<String> FIELD_PATH = AttributeKey.stringKey("graphql.field.path");
  static final AttributeKey<String> FIELD_TYPE = AttributeKey.stringKey("graphql.field.type");
  static final AttributeKey<String> OPERATION = AttributeKey.stringKey("graphql.operation.name");
  static final AttributeKey<String> VARIABLES = AttributeKey.stringKey("graphql.variables.");
  static final AttributeKey<String> ERROR_VALIDATION_NAME =
      AttributeKey.stringKey("graphql.validation.error");

  static final String SPAN_EXECUTE = "graphql.execute";
  static final String SPAN_PARSE = "graphql.parse";
  static final String SPAN_RESOLVE = "graphql.resolve";
  static final String SPAN_VALIDATE = "graphql.validate";
  static final String SPAN_SCHEMA_VALIDATE = "graphql.validateSchema";
  static final String SPAN_SCHEMA_PARSE = "graphql.parseSchema";

  // TODO name
  static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-17.0";

  private GraphQLSingletons() {}
}
