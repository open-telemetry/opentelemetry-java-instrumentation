/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.logback.v1_0_0;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggerContextVO;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import ch.qos.logback.core.spi.AppenderAttachableImpl;
import java.util.Iterator;
import java.util.Map;
import jdk.internal.jline.internal.Nullable;
import org.slf4j.Marker;

public class OpenTelemetryAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
    implements AppenderAttachable<ILoggingEvent> {

  private final AppenderAttachableImpl<ILoggingEvent> aai = new AppenderAttachableImpl<>();

  @Override
  protected void append(ILoggingEvent iLoggingEvent) {

  }

  @Override
  public void addAppender(Appender<ILoggingEvent> appender) {

  }

  @Override
  public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
    return null;
  }

  @Override
  public Appender<ILoggingEvent> getAppender(String s) {
    return null;
  }

  @Override
  public boolean isAttached(Appender<ILoggingEvent> appender) {
    return false;
  }

  @Override
  public void detachAndStopAllAppenders() {

  }

  @Override
  public boolean detachAppender(Appender<ILoggingEvent> appender) {
    return false;
  }

  @Override
  public boolean detachAppender(String s) {
    return false;
  }

}
