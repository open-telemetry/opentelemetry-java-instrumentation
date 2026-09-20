/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import java.util.List;
import javax.annotation.Nullable;

public class RedissonBatchRequest {
  static final int QUERY_TEXT_LIMIT = 32 * 1024;

  private final String operationName;
  @Nullable private final String queryText;
  @Nullable private final Long operationBatchSize;

  public static RedissonBatchRequest create(List<String> commandNames, List<String> queryTexts) {
    String firstCommandName = commandNames.get(0);
    String operationName = "MULTI " + firstCommandName;
    for (int i = 1; i < commandNames.size(); i++) {
      if (!commandNames.get(i).equals(firstCommandName)) {
        operationName = "MULTI";
        break;
      }
    }

    StringBuilder queryText = new StringBuilder();
    for (String commandText : queryTexts) {
      String separator = queryText.length() == 0 ? "" : "; ";
      if (queryText.length() + separator.length() + commandText.length() > QUERY_TEXT_LIMIT) {
        break;
      }
      queryText.append(separator).append(commandText);
    }
    return new RedissonBatchRequest(
        operationName,
        queryText.length() == 0 ? null : queryText.toString(),
        commandNames.size() > 1 ? (long) commandNames.size() : null);
  }

  private RedissonBatchRequest(
      String operationName, @Nullable String queryText, @Nullable Long operationBatchSize) {
    this.operationName = operationName;
    this.queryText = queryText;
    this.operationBatchSize = operationBatchSize;
  }

  public String getOperationName() {
    return operationName;
  }

  @Nullable
  public String getQueryText() {
    return queryText;
  }

  @Nullable
  public Long getOperationBatchSize() {
    return operationBatchSize;
  }
}
