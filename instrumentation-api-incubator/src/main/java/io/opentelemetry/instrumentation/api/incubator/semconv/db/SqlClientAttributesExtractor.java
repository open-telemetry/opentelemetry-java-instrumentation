/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.semconv.AttributeKeyTemplate;
import java.util.Collection;
import java.util.Map;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-spans.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind.
 *
 * <p>It sets the same set of attributes as {@link DbClientAttributesExtractor} plus an additional
 * <code>db.sql.table</code> attribute. The raw SQL statements returned by the {@link
 * SqlClientAttributesGetter#getRawQueryText(Object)} method are sanitized before use, all statement
 * parameters are removed.
 */
public final class SqlClientAttributesExtractor<REQUEST, RESPONSE>
    extends DbClientCommonAttributesExtractor<
        REQUEST, RESPONSE, SqlClientAttributesGetter<REQUEST, RESPONSE>> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_OPERATION = AttributeKey.stringKey("db.operation");
  private static final AttributeKey<String> DB_OPERATION_NAME =
      AttributeKey.stringKey("db.operation.name");
  private static final AttributeKey<String> DB_STATEMENT = AttributeKey.stringKey("db.statement");
  private static final AttributeKey<String> DB_QUERY_TEXT = AttributeKey.stringKey("db.query.text");
  static final AttributeKey<String> DB_COLLECTION_NAME =
      AttributeKey.stringKey("db.collection.name");
  private static final AttributeKey<Long> DB_OPERATION_BATCH_SIZE =
      AttributeKey.longKey("db.operation.batch.size");
  private static final AttributeKeyTemplate<String> DB_OPERATION_PARAMETER =
      AttributeKeyTemplate.stringKeyTemplate("db.operation.parameter");

  /** Creates the SQL client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return SqlClientAttributesExtractor.<REQUEST, RESPONSE>builder(getter).build();
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractorBuilder} that can be used to configure the SQL
   * client attributes extractor.
   */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    return new SqlClientAttributesExtractorBuilder<>(getter);
  }

  private static final String SQL_CALL = "CALL";

  private final AttributeKey<String> oldSemconvTableAttribute;
  private final boolean statementSanitizationEnabled;
  private final boolean operationParameterEnabled;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST, RESPONSE> getter,
      AttributeKey<String> oldSemconvTableAttribute,
      boolean statementSanitizationEnabled,
      boolean operationParameterEnabled) {
    super(getter);
    this.oldSemconvTableAttribute = oldSemconvTableAttribute;
    this.statementSanitizationEnabled = statementSanitizationEnabled;
    this.operationParameterEnabled = operationParameterEnabled;
  }

  @Override
  @SuppressWarnings("AlreadyChecked")
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);

    Collection<String> rawQueryTexts = getter.getRawQueryTexts(request);
    Map<Integer, Object> preparedStatementParameters = getter.getOperationParameters(request);

    if (rawQueryTexts.isEmpty()) {
      return;
    }

    Long batchSize = getter.getBatchSize(request);
    boolean isBatch = batchSize != null && batchSize > 1;

    if (SemconvStability.emitOldDatabaseSemconv()) {
      if (rawQueryTexts.size() == 1) { // for backcompat(?)
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
        String operation = sanitizedStatement.getOperation();
        internalSet(
            attributes,
            DB_STATEMENT,
            statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
        internalSet(attributes, DB_OPERATION, operation);
        if (!SQL_CALL.equals(operation)) {
          internalSet(attributes, oldSemconvTableAttribute, sanitizedStatement.getMainIdentifier());
        }
        setOperationParameters(
            attributes, sanitizedStatement, isBatch, preparedStatementParameters);
      }
    }

    if (SemconvStability.emitStableDatabaseSemconv()) {
      if (isBatch) {
        internalSet(attributes, DB_OPERATION_BATCH_SIZE, batchSize);
      }
      if (rawQueryTexts.size() == 1) {
        String rawQueryText = rawQueryTexts.iterator().next();
        SqlStatementInfo sanitizedStatement = SqlStatementSanitizerUtil.sanitize(rawQueryText);
        String operation = sanitizedStatement.getOperation();
        internalSet(
            attributes,
            DB_QUERY_TEXT,
            statementSanitizationEnabled ? sanitizedStatement.getFullStatement() : rawQueryText);
        internalSet(attributes, DB_OPERATION_NAME, isBatch ? "BATCH " + operation : operation);
        if (!SQL_CALL.equals(operation)) {
          internalSet(attributes, DB_COLLECTION_NAME, sanitizedStatement.getMainIdentifier());
        }
        setOperationParameters(
            attributes, sanitizedStatement, isBatch, preparedStatementParameters);
      } else {
        MultiQuery multiQuery =
            MultiQuery.analyze(getter.getRawQueryTexts(request), statementSanitizationEnabled);
        internalSet(attributes, DB_QUERY_TEXT, join("; ", multiQuery.getStatements()));

        String operation =
            multiQuery.getOperation() != null ? "BATCH " + multiQuery.getOperation() : "BATCH";
        internalSet(attributes, DB_OPERATION_NAME, operation);

        if (multiQuery.getMainIdentifier() != null
            && (multiQuery.getOperation() == null || !SQL_CALL.equals(multiQuery.getOperation()))) {
          internalSet(attributes, DB_COLLECTION_NAME, multiQuery.getMainIdentifier());
        }
      }
    }
  }

  private void setOperationParameters(
      AttributesBuilder attributes,
      SqlStatementInfo sanitizedStatement,
      boolean isBatch,
      Map<Integer, Object> preparedStatementParameters) {
    if (sanitizedStatement.getParameters() != null && operationParameterEnabled && !isBatch) {
      int currentPreparedStatementParametersIndex = 1;
      for (Map.Entry<String, String> entry : sanitizedStatement.getParameters().entrySet()) {
        // in this case it means that the sanitizer parsed an existing ?
        // or a postgres marked parameter. So we'll replace with data from the REQUEST
        String key = entry.getKey();
        String value = entry.getValue();
        if (preparedStatementParameters != null
            && (value.equalsIgnoreCase("?") || value.startsWith("$"))
            && preparedStatementParameters.containsKey(currentPreparedStatementParametersIndex)) {
          internalSet(
              attributes,
              DB_OPERATION_PARAMETER.getAttributeKey(key),
              stringifyParameter(
                  preparedStatementParameters.get(currentPreparedStatementParametersIndex++)));
        } else {
          internalSet(attributes, DB_OPERATION_PARAMETER.getAttributeKey(key), value);
        }
      }
    }
  }

  // String.join is not available on android
  private static String join(String delimiter, Collection<String> collection) {
    StringBuilder builder = new StringBuilder();
    for (String string : collection) {
      if (builder.length() != 0) {
        builder.append(delimiter);
      }
      builder.append(string);
    }
    return builder.toString();
  }

  // TODO define all string repr of objects
  public static String stringifyParameter(Object object) {
    if (object == null) {
      return "<null>";
    } else if (object instanceof String) {
      return String.format("'%s'", object);
    } else if (object instanceof Number) {
      Number number = (Number) object;
      return String.format("%s", number);
    }

    return String.format("<%s>", object.getClass().getSimpleName());
  }
}
