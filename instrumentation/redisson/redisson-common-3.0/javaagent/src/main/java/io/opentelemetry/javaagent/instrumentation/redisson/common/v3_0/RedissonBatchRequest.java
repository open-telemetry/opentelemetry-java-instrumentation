/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import java.util.List;
import javax.annotation.Nullable;

public class RedissonBatchRequest {
  private static final int QUERY_TEXT_LIMIT = 32 * 1024;

  private final String operationName;
  private final String queryText;
  @Nullable private final Long operationBatchSize;

  public static RedissonBatchRequest create(List<String> commandNames, List<String> queryTexts) {
    String firstCommandName = commandNames.get(0);
    String operationName = firstCommandName;
    if (commandNames.size() > 1) {
      operationName = "MULTI " + firstCommandName;
      for (int i = 1; i < commandNames.size(); i++) {
        if (!commandNames.get(i).equals(firstCommandName)) {
          operationName = "MULTI";
          break;
        }
      }
    }

    StringBuilder queryText = new StringBuilder();
    int commandTextLength = 0;
    for (String commandText : queryTexts) {
      if (queryText.length() > 0) {
        queryText.append("; ");
      }
      queryText.append(commandText);
      commandTextLength += commandText.length();
      if (commandTextLength > QUERY_TEXT_LIMIT) {
        break;
      }
    }
    return new RedissonBatchRequest(
        operationName,
        queryText.toString(),
        commandNames.size() > 1 ? (long) commandNames.size() : null);
  }

  private RedissonBatchRequest(
      String operationName, String queryText, @Nullable Long operationBatchSize) {
    this.operationName = operationName;
    this.queryText = queryText;
    this.operationBatchSize = operationBatchSize;
  }

  public String getOperationName() {
    return operationName;
  }

  public String getQueryText() {
    return queryText;
  }

  @Nullable
  public Long getOperationBatchSize() {
    return operationBatchSize;
  }
}
