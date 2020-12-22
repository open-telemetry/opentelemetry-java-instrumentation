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
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springbatch.TestItemProcessor
import springbatch.TestItemReader
import springbatch.TestItemWriter
import springbatch.TestTasklet

@Configuration
@EnableBatchProcessing
class SpringBatchApplication {

  @Autowired
  JobBuilderFactory jobs
  @Autowired
  StepBuilderFactory steps

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
}
