/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import javax.annotation.Nullable;

public class InjectionState {
  private static final int ALREADY_INJECTED_FAKE_VALUE = -1;
  private static final int HEAD_TAG_LENGTH = "<head>".length();

  private final String characterEncoding;
  @Nullable private final SnippetInjectingResponseWrapper wrapper;
  private int headTagBytesSeen = 0;

  public InjectionState(SnippetInjectingResponseWrapper wrapper) {
    this.wrapper = wrapper;
    this.characterEncoding = wrapper.getCharacterEncoding();
  }

  public InjectionState(String characterEncoding) {
    this.characterEncoding = characterEncoding;
    this.wrapper = null;
  }

  public int getHeadTagBytesSeen() {
    return headTagBytesSeen;
  }

  public String getCharacterEncoding() {
    return characterEncoding;
  }

  public void setAlreadyInjected() {
    headTagBytesSeen = ALREADY_INJECTED_FAKE_VALUE;
  }

  public boolean isAlreadyInjected() {
    return headTagBytesSeen == ALREADY_INJECTED_FAKE_VALUE;
  }

  /**
   * Returns true when the byte is the last character of "<head>" and now is the right time to
   * inject. Otherwise, returns false.
   */
  public boolean processByte(int b) {
    if (isAlreadyInjected()) {
      return false;
    }
    if (inHeadTag(b)) {
      headTagBytesSeen++;
    } else {
      headTagBytesSeen = 0;
    }
    return headTagBytesSeen == HEAD_TAG_LENGTH;
  }

  private boolean inHeadTag(int b) {
    if (headTagBytesSeen == 0 && b == '<') {
      return true;
    } else if (headTagBytesSeen == 1 && b == 'h') {
      return true;
    } else if (headTagBytesSeen == 2 && b == 'e') {
      return true;
    } else if (headTagBytesSeen == 3 && b == 'a') {
      return true;
    } else if (headTagBytesSeen == 4 && b == 'd') {
      return true;
    } else if (headTagBytesSeen == 5 && b == '>') {
      return true;
    }
    return false;
  }

  public SnippetInjectingResponseWrapper getWrapper() {
    return wrapper;
  }
}
