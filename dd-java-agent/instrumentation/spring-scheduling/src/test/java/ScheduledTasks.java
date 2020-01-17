/*
 * Copyright 2012-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import datadog.trace.api.DDTags;
import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.trace_annotation.TraceDecorator.DECORATE;

@Component
public class ScheduledTasks {

  private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

  public static boolean reportCurrentTimeExecuted = false;

  @Scheduled(fixedRate = 5000)
  //  @Trace
  public void reportCurrentTime() {
    //    log.info("The time is now {}", dateFormat.format(new Date()));
    // test that the body of method has been executed
    // create span

    //    activeSpan().setTag(DDTags.SERVICE_NAME, "test");
    reportCurrentTimeExecuted = true;
  }

  public void runSpan() {
    // create span
    final AgentSpan span = startSpan("currentTime");
    DECORATE.afterStart(span);

    try (final AgentScope scope = activateSpan(span, false)) {
      activeSpan().setTag(DDTags.SERVICE_NAME, "test");
      DECORATE.afterStart(span);
      scope.setAsyncPropagation(true);

      try {
        reportCurrentTime();
      } catch (final Throwable throwable) {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.finish();
        throw throwable;
      }
    }
  }
}
