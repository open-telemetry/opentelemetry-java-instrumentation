/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.snippet;

// this is shared by both ServletOutputStream and PrintWriter injection
public class InjectionState {
  private static final int HEAD_TAG_WRITTEN_FAKE_VALUE = -1;
  private static final int HEAD_TAG_LENGTH = "<head>".length();
  private final SnippetInjectingResponseWrapper wrapper;
  private int headTagBytesSeen = 0;

  public InjectionState(SnippetInjectingResponseWrapper wrapper) {
    this.wrapper = wrapper;
  }

  public int getHeadTagBytesSeen() {
    return headTagBytesSeen;
  }

  public String getCharacterEncoding() {
    return wrapper.getCharacterEncoding();
  }

  private void setHeadTagWritten() {
    headTagBytesSeen = HEAD_TAG_WRITTEN_FAKE_VALUE;
  }

  public boolean isHeadTagWritten() {
    return headTagBytesSeen == HEAD_TAG_WRITTEN_FAKE_VALUE;
  }

  /**
   * Returns true when the byte is the last character of "<head>" and now is the right time to
   * inject. Otherwise, returns false.
   */
  public boolean processByte(int b) {
    if (isHeadTagWritten()) {
      return false;
    }
    if (inHeadTag(b)) {
      headTagBytesSeen++;
    } else {
      headTagBytesSeen = 0;
    }
    if (headTagBytesSeen == HEAD_TAG_LENGTH) {
      setHeadTagWritten();
      return true;
    } else {
      return false;
    }
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
