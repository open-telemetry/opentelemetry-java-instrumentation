package com.datadoghq.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.junit.Test;

public class AWSInstrumentationTest {

  @Test
  public void test() {

    // Build AWS client with TracingRequestHandler e.g.
    AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
    builder.withRegion(Regions.US_EAST_1);
    builder.build();

    assertThat(builder.getRequestHandlers()).isNotNull();
    assertThat(builder.getRequestHandlers().size()).isEqualTo(1);
    assertThat(builder.getRequestHandlers().get(0).getClass().getSimpleName())
        .isEqualTo("TracingRequestHandler");
  }
}
