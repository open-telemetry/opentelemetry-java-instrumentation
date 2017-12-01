package com.datadoghq.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.datadoghq.trace.DDBaseSpan;
import com.datadoghq.trace.DDTracer;
import com.datadoghq.trace.writer.ListWriter;
import io.opentracing.tag.Tags;
import java.net.URI;
import java.util.List;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ApacheHTTPClientTest {
  private static final ListWriter writer = new ListWriter();
  private static final DDTracer tracer = new DDTracer(writer);

  @BeforeClass
  public static void setup() throws Exception {
    TestUtils.registerOrReplaceGlobalTracer(tracer);
    TestHttpServer.startServer();
  }

  @AfterClass
  public static void stopServer() throws Exception {
    TestHttpServer.stopServer();
  }

  @Before
  public void beforeEachTest() {
    writer.clear();
  }

  @Test
  public void propagatedTrace() throws Exception {
    final HttpClientBuilder builder = HttpClientBuilder.create();

    final HttpClient client = builder.build();
    TestUtils.runUnderTrace(
        "someTrace",
        new Runnable() {
          @Override
          public void run() {
            try {
              HttpResponse response =
                  client.execute(new HttpGet(new URI("http://localhost:" + TestHttpServer.PORT)));
              assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            } catch (Exception e) {
              e.printStackTrace();
              Assert.fail("Error running client");
            }
          }
        });
    // one trace on the server, one trace on the client
    assertThat(writer.size()).isEqualTo(2);
    final List<DDBaseSpan<?>> serverTrace = writer.get(0);
    assertThat(serverTrace.size()).isEqualTo(1);

    final List<DDBaseSpan<?>> clientTrace = writer.get(1);
    assertThat(clientTrace.size()).isEqualTo(3);
    assertThat(clientTrace.get(0).getOperationName()).isEqualTo("someTrace");
    // our instrumentation makes 2 spans for apache-httpclient
    final DDBaseSpan<?> localSpan = clientTrace.get(1);
    assertThat(localSpan.getTags().get(Tags.COMPONENT.getKey())).isEqualTo("apache-httpclient");
    assertThat(localSpan.getOperationName()).isEqualTo("GET");

    final DDBaseSpan<?> clientSpan = clientTrace.get(2);
    assertThat(clientSpan.getOperationName()).isEqualTo("GET");
    assertThat(clientSpan.getTags().get(Tags.HTTP_METHOD.getKey())).isEqualTo("GET");
    assertThat(clientSpan.getTags().get(Tags.HTTP_STATUS.getKey())).isEqualTo(200);
    assertThat(clientSpan.getTags().get(Tags.HTTP_URL.getKey())).isEqualTo("http://localhost:8089");
    assertThat(clientSpan.getTags().get(Tags.PEER_HOSTNAME.getKey())).isEqualTo("localhost");
    assertThat(clientSpan.getTags().get(Tags.PEER_PORT.getKey())).isEqualTo(8089);
    assertThat(clientSpan.getTags().get(Tags.SPAN_KIND.getKey())).isEqualTo(Tags.SPAN_KIND_CLIENT);

    // client trace propagates to server
    assertThat(clientSpan.getTraceId()).isEqualTo(serverTrace.get(0).getTraceId());
    // server span is parented under http client
    assertThat(clientSpan.getSpanId()).isEqualTo(serverTrace.get(0).getParentId());
  }

  @Test
  public void unPropagatedTrace() throws Exception {
    final HttpClientBuilder builder = HttpClientBuilder.create();

    final HttpClient client = builder.build();
    TestUtils.runUnderTrace(
        "someTrace",
        new Runnable() {
          @Override
          public void run() {
            try {
              HttpResponse response =
                  client.execute(
                      new HttpGet(
                          new URI(
                              "http://localhost:"
                                  + TestHttpServer.PORT
                                  + "?"
                                  + TestHttpServer.IS_DD_SERVER
                                  + "=false")));
              assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);
            } catch (Exception e) {
              e.printStackTrace();
              Assert.fail("Error running client");
            }
          }
        });
    // only one trace (client).
    assertThat(writer.size()).isEqualTo(1);
    final List<DDBaseSpan<?>> clientTrace = writer.get(0);
    assertThat(clientTrace.size()).isEqualTo(3);
    assertThat(clientTrace.get(0).getOperationName()).isEqualTo("someTrace");
    // our instrumentation makes 2 spans for apache-httpclient
    final DDBaseSpan<?> localSpan = clientTrace.get(1);
    assertThat(localSpan.getTags().get(Tags.COMPONENT.getKey())).isEqualTo("apache-httpclient");
    assertThat(localSpan.getOperationName()).isEqualTo("GET");

    final DDBaseSpan<?> clientSpan = clientTrace.get(2);
    assertThat(clientSpan.getOperationName()).isEqualTo("GET");
    assertThat(clientSpan.getTags().get(Tags.HTTP_METHOD.getKey())).isEqualTo("GET");
    assertThat(clientSpan.getTags().get(Tags.HTTP_STATUS.getKey())).isEqualTo(200);
    assertThat(clientSpan.getTags().get(Tags.HTTP_URL.getKey()))
        .isEqualTo("http://localhost:8089?is-dd-server=false");
    assertThat(clientSpan.getTags().get(Tags.PEER_HOSTNAME.getKey())).isEqualTo("localhost");
    assertThat(clientSpan.getTags().get(Tags.PEER_PORT.getKey())).isEqualTo(8089);
    assertThat(clientSpan.getTags().get(Tags.SPAN_KIND.getKey())).isEqualTo(Tags.SPAN_KIND_CLIENT);
  }
}
