/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner;

import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.CustomEventChunkListener;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.CustomEventItemProcessListener;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.CustomEventItemReadListener;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.CustomEventItemWriteListener;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.CustomEventJobListener;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.CustomEventStepListener;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.SingleItemReader;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestDecider;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestItemProcessor;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestItemReader;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestItemWriter;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestPartitionedItemReader;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestPartitioner;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestSyncItemReader;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.springbatch.TestTasklet;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.SimpleJobLauncher;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableBatchProcessing
public class SpringBatchApplication {

  @Autowired JobBuilderFactory jobs;
  @Autowired StepBuilderFactory steps;
  @Autowired JobRepository jobRepository;

  @Bean
  AsyncTaskExecutor asyncTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(10);
    executor.setMaxPoolSize(10);
    return executor;
  }

  @Bean
  JobLauncher jobLauncher() {
    SimpleJobLauncher launcher = new SimpleJobLauncher();
    launcher.setJobRepository(jobRepository);
    launcher.setTaskExecutor(asyncTaskExecutor());
    return launcher;
  }

  // common
  @Bean
  ItemReader<String> itemReader() {
    return new TestItemReader();
  }

  @Bean
  ItemProcessor<String, Integer> itemProcessor() {
    return new TestItemProcessor();
  }

  @Bean
  ItemWriter<Integer> itemWriter() {
    return new TestItemWriter();
  }

  // simple tasklet job
  @Bean
  Job taskletJob() {
    return jobs.get("taskletJob").start(step()).build();
  }

  @Bean
  Step step() {
    return steps.get("step").tasklet(new TestTasklet()).build();
  }

  // 2-step tasklet + chunked items job
  @Bean
  Job itemsAndTaskletJob() {
    return jobs.get("itemsAndTaskletJob").start(itemStep()).next(taskletStep()).build();
  }

  @Bean
  Step taskletStep() {
    return steps.get("taskletStep").tasklet(new TestTasklet()).build();
  }

  @Bean
  Step itemStep() {
    return steps
        .get("itemStep")
        .<String, Integer>chunk(5)
        .reader(itemReader())
        .processor(itemProcessor())
        .writer(itemWriter())
        .build();
  }

  // parallel items job
  @Bean
  Job parallelItemsJob() {
    return jobs.get("parallelItemsJob").start(parallelItemsStep()).build();
  }

  @Bean
  Step parallelItemsStep() {
    return steps
        .get("parallelItemsStep")
        .<String, Integer>chunk(2)
        .reader(new TestSyncItemReader(5))
        .processor(itemProcessor())
        .writer(itemWriter())
        .taskExecutor(asyncTaskExecutor())
        .throttleLimit(2)
        .build();
  }

  // job using a flow
  @Bean
  Job flowJob() {
    return jobs.get("flowJob").start(flow()).build().build();
  }

  @Bean
  Flow flow() {
    return new FlowBuilder<SimpleFlow>("flow").start(flowStep1()).on("*").to(flowStep2()).build();
  }

  @Bean
  Step flowStep1() {
    return steps.get("flowStep1").tasklet(new TestTasklet()).build();
  }

  @Bean
  Step flowStep2() {
    return steps.get("flowStep2").tasklet(new TestTasklet()).build();
  }

  // split job
  @Bean
  Job splitJob() {
    return jobs.get("splitJob")
        .start(splitFlowStep1())
        .split(asyncTaskExecutor())
        .add(splitFlow2())
        .build()
        .build();
  }

  @Bean
  Step splitFlowStep1() {
    return steps.get("splitFlowStep1").tasklet(new TestTasklet()).build();
  }

  @Bean
  Flow splitFlow2() {
    return new FlowBuilder<SimpleFlow>("splitFlow2").start(splitFlowStep2()).build();
  }

  @Bean
  Step splitFlowStep2() {
    return steps.get("splitFlowStep2").tasklet(new TestTasklet()).build();
  }

  // job with decisions
  @Bean
  Job decisionJob() {
    return jobs.get("decisionJob")
        .start(decisionStepStart())
        .next(new TestDecider())
        .on("LEFT")
        .to(decisionStepLeft())
        .on("RIGHT")
        .to(decisionStepRight())
        .end()
        .build();
  }

  @Bean
  Step decisionStepStart() {
    return steps.get("decisionStepStart").tasklet(new TestTasklet()).build();
  }

  @Bean
  Step decisionStepLeft() {
    return steps.get("decisionStepLeft").tasklet(new TestTasklet()).build();
  }

  @Bean
  Step decisionStepRight() {
    return steps.get("decisionStepRight").tasklet(new TestTasklet()).build();
  }

  // partitioned job
  @Bean
  Job partitionedJob() {
    return jobs.get("partitionedJob").start(partitionManagerStep()).build();
  }

  @Bean
  Step partitionManagerStep() {
    return steps
        .get("partitionManagerStep")
        .partitioner("partitionWorkerStep", partitioner())
        .step(partitionWorkerStep())
        .gridSize(2)
        .taskExecutor(asyncTaskExecutor())
        .build();
  }

  @Bean
  Partitioner partitioner() {
    return new TestPartitioner();
  }

  @Bean
  Step partitionWorkerStep() {
    return steps
        .get("partitionWorkerStep")
        .<String, Integer>chunk(5)
        .reader(partitionedItemReader())
        .processor(itemProcessor())
        .writer(itemWriter())
        .build();
  }

  @Bean
  ItemReader<String> partitionedItemReader() {
    return new TestPartitionedItemReader();
  }

  // custom span events items job
  @Bean
  Job customSpanEventsItemsJob() {
    return jobs.get("customSpanEventsItemsJob")
        .start(customSpanEventsItemStep())
        .listener(new CustomEventJobListener())
        .build();
  }

  @Bean
  Step customSpanEventsItemStep() {
    return steps
        .get("customSpanEventsItemStep")
        .<String, Integer>chunk(5)
        .reader(new SingleItemReader())
        .processor(itemProcessor())
        .writer(itemWriter())
        .listener(new CustomEventStepListener())
        .listener(new CustomEventChunkListener())
        .listener(new CustomEventItemReadListener())
        .listener(new CustomEventItemProcessListener())
        .listener(new CustomEventItemWriteListener())
        .build();
  }
}
