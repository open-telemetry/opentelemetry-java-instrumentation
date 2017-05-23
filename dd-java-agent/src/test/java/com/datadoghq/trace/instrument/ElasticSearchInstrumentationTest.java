package com.datadoghq.trace.instrument;

import io.opentracing.Tracer;
import io.opentracing.contrib.spanmanager.DefaultSpanManager;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.GlobalTracer;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.transport.Netty3Plugin;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

/**
 * Created by gpolaert on 5/23/17.
 */
public class ElasticSearchInstrumentationTest {


    private static final int HTTP_PORT = 9205;
    private static final String HTTP_TRANSPORT_PORT = "9305";
    private static final String ES_WORKING_DIR = "target/es";
    private static String clusterName = "cluster-name";
    private static Node node;
    private static MockTracer tracer;

    @BeforeClass
    public static void startElasticsearch() throws Exception {

        tracer = new MockTracer();
        GlobalTracer.register(tracer);


        Settings settings = Settings.builder()
                .put("path.home", ES_WORKING_DIR)
                .put("path.data", ES_WORKING_DIR + "/data")
                .put("path.logs", ES_WORKING_DIR + "/logs")
                .put("transport.type", "netty3")
                .put("http.type", "netty3")
                .put("cluster.name", clusterName)
                .put("http.port", HTTP_PORT)
                .put("transport.tcp.port", HTTP_TRANSPORT_PORT)
                .put("network.host", "127.0.0.1")
                .build();
        Collection plugins = Collections.singletonList(Netty3Plugin.class);
        node = new PluginConfigurableNode(settings, plugins);
        node.start();

    }

    @AfterClass
    public static void stopElasticsearch() throws Exception {
        node.close();
    }

    @Before
    public void setUp() {
        DefaultSpanManager.getInstance().activate(tracer.buildSpan("parent").start());
    }


    @After
    public void tearDown() {
        DefaultSpanManager.getInstance().current().close();
    }

    @Test
    public void Test() throws IOException {

        Settings settings = Settings.builder()
                .put("cluster.name", clusterName).build();


        TransportClient client = new PreBuiltTransportClient(settings)
                .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"),
                        Integer.parseInt(HTTP_TRANSPORT_PORT)));

        IndexRequest indexRequest = new IndexRequest("twitter").type("tweet").id("1").
                source(jsonBuilder()
                        .startObject()
                        .field("user", "kimchy")
                        .field("postDate", new Date())
                        .field("message", "trying out Elasticsearch")
                        .endObject()
                );

        IndexResponse indexResponse = client.index(indexRequest).actionGet();


        client.close();
        DefaultSpanManager.getInstance().current().close();
        assertThat(tracer.finishedSpans().size()).isEqualTo(2);
    }


    private static class PluginConfigurableNode extends Node {
        public PluginConfigurableNode(Settings settings, Collection<Class<? extends Plugin>> classpathPlugins) {
            super(InternalSettingsPreparer.prepareEnvironment(settings, null), classpathPlugins);
        }
    }

}
