/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

public interface SnippetInjectingResponseWrapper {

  boolean isIncludeRequest();

  boolean isContentTypeTextHtml();

  boolean isCurrentRequest();

  boolean updateContentLengthIfPreviouslySet();

  void resetContentLengthIfPreviouslyAdjusted();

  boolean isNotSafeToInject();

  String getCharacterEncoding();
}
