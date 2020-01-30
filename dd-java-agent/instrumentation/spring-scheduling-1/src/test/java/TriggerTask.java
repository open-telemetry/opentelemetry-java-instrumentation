import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
// import datadog.trace.api.DDTags;
// import datadog.trace.instrumentation.api.AgentScope;
// import datadog.trace.instrumentation.api.AgentSpan;

@Component
public class TriggerTask implements Runnable {

  public TriggerTask() {
    System.out.println("in the ScheduledTasks constructor");
  }

  private final CountDownLatch latch = new CountDownLatch(1);

  @Scheduled(cron = "0/5 * * * * *")
  @Override
  public void run() {
    latch.countDown();
  }

  public void blockUntilExecute() throws InterruptedException {
    latch.await(5, TimeUnit.SECONDS);
  }
}
