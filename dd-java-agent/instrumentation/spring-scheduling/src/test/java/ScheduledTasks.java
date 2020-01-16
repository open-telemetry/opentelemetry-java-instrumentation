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
import datadog.trace.api.Trace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

import static datadog.trace.instrumentation.api.AgentTracer.activeSpan;

@Component
public class ScheduledTasks {

  private static final Logger log = LoggerFactory.getLogger(ScheduledTasks.class);

  private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

  public static boolean reportCurrentTimeExecuted = false;

  @Scheduled(fixedRate = 5000)
  @Trace
  public void reportCurrentTime() {
    //    log.info("The time is now {}", dateFormat.format(new Date()));
    // test that the body of method has been executed
    activeSpan().setTag(DDTags.SERVICE_NAME, "test");
    reportCurrentTimeExecuted = true;
  }
}
