/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

public interface SnippetInjectingResponseWrapper {

  boolean isContentTypeTextHtml();

  boolean isCurrentRequest();

  void updateContentLengthIfPreviouslySet();

  boolean isNotSafeToInject();

  String getCharacterEncoding();
}
