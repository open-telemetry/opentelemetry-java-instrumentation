/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.concurrent.CountDownLatch;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

@Service
public class LambdaTaskConfigurer implements SchedulingConfigurer {

  public final CountDownLatch singleUseLatch = new CountDownLatch(1);

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    taskRegistrar.addFixedDelayTask(singleUseLatch::countDown, 500);
  }
}
