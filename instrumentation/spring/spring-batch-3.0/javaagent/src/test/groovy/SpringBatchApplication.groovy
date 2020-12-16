/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springbatch.TestItemProcessor
import springbatch.TestItemReader
import springbatch.TestItemWriter
import springbatch.TestTasklet

@Configuration
@EnableBatchProcessing
class SpringBatchApplication {
  @Bean
  Job taskletJob(@Qualifier("step") Step taskletStep, JobBuilderFactory jobBuilderFactory) {
    jobBuilderFactory.get("taskletJob")
      .start(taskletStep)
      .build()
  }

  @Bean
  Step step(StepBuilderFactory stepBuilderFactory) {
    stepBuilderFactory.get("step")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Job itemsAndTaskletJob(@Qualifier("itemStep") Step itemStep,
                         @Qualifier("taskletStep") Step taskletStep,
                         JobBuilderFactory jobBuilderFactory) {
    jobBuilderFactory.get("itemsAndTaskletJob")
      .flow(itemStep)
      .next(taskletStep)
      .end()
      .build()
  }

  @Bean
  Step taskletStep(StepBuilderFactory stepBuilderFactory) {
    stepBuilderFactory.get("taskletStep")
      .tasklet(new TestTasklet())
      .build()
  }

  @Bean
  Step itemStep(ItemReader<String> itemReader,
                ItemProcessor<String, Integer> itemProcessor,
                ItemWriter<Integer> itemWriter,
                StepBuilderFactory stepBuilderFactory) {
    stepBuilderFactory.get("itemStep")
      .chunk(5)
      .reader(itemReader)
      .processor(itemProcessor)
      .writer(itemWriter)
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
}
