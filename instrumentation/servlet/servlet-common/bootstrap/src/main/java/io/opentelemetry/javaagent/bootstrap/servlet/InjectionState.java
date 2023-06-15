/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

// this is shared by both ServletOutputStream and PrintWriter injection
public class InjectionState {
  private static final int HEAD_TAG_WRITTEN_FAKE_VALUE = -1;
  private static final String HEAD_PREFIX = "<head";
  private static final StringBuilder HEAD_PREFIX_BUILDER = new StringBuilder("");
  private final SnippetInjectingResponseWrapper wrapper;
  private int headTagBytesSeen = 0;
  private boolean isContainHead = false;

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
    inHeadTag(b);
    return isHeadTagWritten();
  }

  private void inHeadTag(int b) {
    if (b == '<' && !isContainHead) {
      HEAD_PREFIX_BUILDER.append("<");
    } else if (b == 'h' && !isContainHead) {
      HEAD_PREFIX_BUILDER.append("h");
    } else if (b == 'e' && !isContainHead) {
      HEAD_PREFIX_BUILDER.append("e");
    } else if (b == 'a' && !isContainHead) {
      HEAD_PREFIX_BUILDER.append("a");
    } else if (b == 'd' && !isContainHead) {
      HEAD_PREFIX_BUILDER.append("d");
      if (HEAD_PREFIX_BUILDER.toString().equals(HEAD_PREFIX)) {
        isContainHead = true;
      }
    } else if (b == '>') {
      if (isContainHead) {
        setHeadTagWritten();
      }
      HEAD_PREFIX_BUILDER.delete(0,HEAD_PREFIX_BUILDER.length());
    }
  }

  public SnippetInjectingResponseWrapper getWrapper() {
    return wrapper;
  }
}
