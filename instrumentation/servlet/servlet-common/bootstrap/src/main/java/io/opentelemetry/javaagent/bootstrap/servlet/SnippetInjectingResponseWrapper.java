/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

public interface SnippetInjectingResponseWrapper {

  boolean isContentTypeTextHtml();

  void updateContentLengthIfPreviouslySet();

  boolean isNotSafeToInject();

  String getCharacterEncoding();
}
