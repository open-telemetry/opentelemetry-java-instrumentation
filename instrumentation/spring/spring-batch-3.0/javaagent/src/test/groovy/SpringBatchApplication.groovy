/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.FlowBuilder
import org.springframework.batch.core.job.flow.Flow
import org.springframework.batch.core.job.flow.support.SimpleFlow
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.batch.core.launch.support.SimpleJobLauncher
import org.springframework.batch.core.partition.support.Partitioner
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import springbatch.CustomEventChunkListener
import springbatch.CustomEventItemProcessListener
import springbatch.CustomEventItemReadListener
import springbatch.CustomEventItemWriteListener
import springbatch.CustomEventJobListener
import springbatch.CustomEventStepListener
import springbatch.SingleItemReader
import springbatch.TestDecider
import springbatch.TestItemProcessor
import springbatch.TestItemReader
import springbatch.TestItemWriter
import springbatch.TestPartitionedItemReader
import springbatch.TestPartitioner
import springbatch.TestSyncItemReader
import springbatch.TestTasklet

@Configuration
@EnableBatchProcessing
class SpringBatchApplication {

  @Autowired
  JobBuilderFactory jobs
  @Autowired
  StepBuilderFactory steps
  @Autowired
  JobRepository jobRepository

  @Bean
  AsyncTaskExecutor asyncTaskExecutor() {
    def executor = new ThreadPoolTaskExecutor()
    executor.corePoolSize = 10
    executor.maxPoolSize = 10
    executor
  }

  @Bean
  JobLauncher jobLauncher() {
    def launcher = new SimpleJobLauncher()
    launcher.jobRepository = jobRepository
    launcher.taskExecutor = asyncTaskExecutor()
    launcher
  }

  // common
  @Bean
  ItemReader<String> itemReader() {
    new TestItemReader()
  }

  @Bean
  ItemProcessor<String, Integer> itemProcessor() {
    new TestItemProcessor()
  }

  @Bean
  ItemWriter<Integer> itemWriter() {
    new TestItemWriter()
  }

  // simple tasklet job
  @Bean
  Job taskletJob() {
    jobs.get("taskletJob")
      .start(step())
      .build()
  }

  @Bean
  Step step() {
    steps.get("step")
      .tasklet(new TestTasklet())
      .build()
  }

  // 2-step tasklet + chunked items job
  @Bean
  Job itemsAndTaskletJob() {
    jobs.get("itemsAndTaskletJob")
      .start(itemStep())
      .next(taskletStep())
      .build()
  }

  @Bean
  Step taskletStep() {
    steps.get("taskletStep")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Step itemStep() {
    steps.get("itemStep")
      .chunk(5)
      .reader(itemReader())
      .processor(itemProcessor())
      .writer(itemWriter())
      .build()
  }

  // parallel items job
  @Bean
  Job parallelItemsJob() {
    jobs.get("parallelItemsJob")
      .start(parallelItemsStep())
      .build()
  }

  @Bean
  Step parallelItemsStep() {
    steps.get("parallelItemsStep")
      .chunk(2)
      .reader(new TestSyncItemReader(5))
      .processor(itemProcessor())
      .writer(itemWriter())
      .taskExecutor(asyncTaskExecutor())
      .throttleLimit(2)
      .build()
  }

  // job using a flow
  @Bean
  Job flowJob() {
    jobs.get("flowJob")
      .start(flow())
      .build()
      .build()
  }

  @Bean
  Flow flow() {
    new FlowBuilder<SimpleFlow>("flow")
      .start(flowStep1())
      .on("*")
      .to(flowStep2())
      .build()
  }

  @Bean
  Step flowStep1() {
    steps.get("flowStep1")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Step flowStep2() {
    steps.get("flowStep2")
      .tasklet(new TestTasklet())
      .build()
  }

  // split job
  @Bean
  Job splitJob() {
    jobs.get("splitJob")
      .start(splitFlowStep1())
      .split(asyncTaskExecutor())
      .add(splitFlow2())
      .build()
      .build()
  }

  @Bean
  Step splitFlowStep1() {
    steps.get("splitFlowStep1")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Flow splitFlow2() {
    new FlowBuilder<SimpleFlow>("splitFlow2")
      .start(splitFlowStep2())
      .build()
  }

  @Bean
  Step splitFlowStep2() {
    steps.get("splitFlowStep2")
      .tasklet(new TestTasklet())
      .build()
  }

  // job with decisions
  @Bean
  Job decisionJob() {
    jobs.get("decisionJob")
      .start(decisionStepStart())
      .next(new TestDecider())
      .on("LEFT").to(decisionStepLeft())
      .on("RIGHT").to(decisionStepRight())
      .end()
      .build()
  }

  @Bean
  Step decisionStepStart() {
    steps.get("decisionStepStart")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Step decisionStepLeft() {
    steps.get("decisionStepLeft")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Step decisionStepRight() {
    steps.get("decisionStepRight")
      .tasklet(new TestTasklet())
      .build()
  }

  // partitioned job
  @Bean
  Job partitionedJob() {
    jobs.get("partitionedJob")
      .start(partitionManagerStep())
      .build()
  }

  @Bean
  Step partitionManagerStep() {
    steps.get("partitionManagerStep")
      .partitioner("partitionWorkerStep", partitioner())
      .step(partitionWorkerStep())
      .gridSize(2)
      .taskExecutor(asyncTaskExecutor())
      .build()
  }

  @Bean
  Partitioner partitioner() {
    new TestPartitioner()
  }

  @Bean
  Step partitionWorkerStep() {
    steps.get("partitionWorkerStep")
      .chunk(5)
      .reader(partitionedItemReader())
      .processor(itemProcessor())
      .writer(itemWriter())
      .build()
  }

  @Bean
  ItemReader<String> partitionedItemReader() {
    new TestPartitionedItemReader()
  }

  // custom span events items job
  @Bean
  Job customSpanEventsItemsJob() {
    jobs.get("customSpanEventsItemsJob")
      .start(customSpanEventsItemStep())
      .listener(new CustomEventJobListener())
      .build()
  }

  @Bean
  Step customSpanEventsItemStep() {
    steps.get("customSpanEventsItemStep")
      .chunk(5)
      .reader(new SingleItemReader())
      .processor(itemProcessor())
      .writer(itemWriter())
      .listener(new CustomEventStepListener())
      .listener(new CustomEventChunkListener())
      .listener(new CustomEventItemReadListener())
      .listener(new CustomEventItemProcessListener())
      .listener(new CustomEventItemWriteListener())
      .build()
  }
}
