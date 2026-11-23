/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

public interface SnippetInjectingResponseWrapper {

  boolean isContentTypeTextHtml();

  boolean isCurrentRequest();

  default boolean isCurrentIncludeResponse(Object response) {
    return false;
  }

  void updateContentLengthIfPreviouslySet();

  void resetContentLengthIfPreviouslyAdjusted();

  boolean isNotSafeToInject();

  String getCharacterEncoding();
}
