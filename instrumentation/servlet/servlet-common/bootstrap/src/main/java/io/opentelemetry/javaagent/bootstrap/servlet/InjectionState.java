/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// this is shared by both ServletOutputStream and PrintWriter injection
public class InjectionState {
  private static final int HEAD_TAG_WRITTEN_FAKE_VALUE = -1;
  private final SnippetInjectingResponseWrapper wrapper;
  private int headTagBytesSeen = 0;
  private final Map<String, Integer> tagCount = new HashMap<>();
  private final Map<String, AtomicInteger> headAppearCount = new HashMap<>();

  public InjectionState(SnippetInjectingResponseWrapper wrapper) {
    this.wrapper = wrapper;
    List<String> headTagList = ExperimentalSnippetHolder.getHeadTagList();
    if (headTagList != null) {
      for (String tag : headTagList) {
        tagCount.put(tag, tag.length());
        headAppearCount.put(tag, new AtomicInteger(0));
      }
    }
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
    if (!tagCount.isEmpty()) {
      tagCount.forEach(
          (headTag, count) -> {
            char[] headTagBytes = headTag.toCharArray();
            if (headTagBytes[headAppearCount.get(headTag).get()] == b) {
              if (headAppearCount.get(headTag).get() == count - 1) {
                setHeadTagWritten();
              } else {
                headAppearCount.get(headTag).addAndGet(1);
              }
            } else {
              headAppearCount.get(headTag).set(0);
            }
          });
    }
  }

  public SnippetInjectingResponseWrapper getWrapper() {
    return wrapper;
  }
}
