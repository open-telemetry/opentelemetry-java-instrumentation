package io.opentelemetry.instrumentation.jmx.rules.kafka;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import java.time.Duration;

public class KafkaContainer extends GenericContainer<KafkaContainer> {


  private static final int KAFKA_CONTROLLER_PORT = 9093;

  private static final String KAFKA_ALIAS = "kafka";
  private static final int KAFKA_PORT = 9092;

  private static final String CONNECT_ALIAS = "kafka-connect";
  private static final int CONNECT_PORT = 8083;
  private final boolean bitnamiImage;

  private Mode mode;
  private String zookeeperAddress;
  private final String basePath;

  private enum Mode {
    // default mode for new kafka versions
    KRAFT,
    // zookeeper was required for older versions of kafka
    ZOOKEEPER,
    // kafka connect runs alongside kafka
    CONNECT
  }

  public static KafkaContainer create(String image) {
    return new KafkaContainer(image);
  }

  private KafkaContainer(String image) {
    super(image);
    this.withStartupTimeout(Duration.ofMinutes(1));
    this.mode = Mode.KRAFT;
    this.bitnamiImage = image.startsWith("bitnami");
    this.basePath = bitnamiImage ? "/opt/bitnami/kafka" : "/opt/kafka";
  }

  public KafkaContainer withZookeeper(String host, int port) {
    this.mode = Mode.ZOOKEEPER;
    this.zookeeperAddress = host + ":" + port;
    return this;
  }

  public KafkaContainer withKafkaConnect() {
    this.mode = Mode.CONNECT;
    return this;
  }

  @Override
  protected void doStart() {
    switch (mode) {
      case KRAFT:
        this.configureKRaft();
        break;
      case ZOOKEEPER:
        this.configureZookeeper(zookeeperAddress);
        break;
      case CONNECT:
        this.configureKafkaConnect();
        break;
    }

    super.doStart();
    // send sample message to ensure we get log flush metrics
    sendSampleMessage();
  }

  private  void sendSampleMessage() {
    try {
      execInContainer(
          "/bin/sh",
          "-c",
          // we have to force empty JAVA_TOOL_OPTIONS to prevent agent being loaded on producer
          "echo 'my message' | JAVA_TOOL_OPTIONS='' "+basePath + "/bin/kafka-console-producer.sh"
              + " --bootstrap-server localhost:"
              + KAFKA_PORT
              + " --topic my-topic");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to send sample message to Kafka", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to send sample message to Kafka", e);
    }
  }

  private void configureKRaft() {
    String serverPropertiesPath = basePath + "/config/kraft/server.properties";
    String kafkaCommand =
        basePath + "/bin/kafka-storage.sh format -t $("+ basePath +"/bin/kafka-storage.sh random-uuid) -c "
            + serverPropertiesPath
            + " && " + basePath + "/bin/kafka-server-start.sh "
            + serverPropertiesPath;

    this.withNetworkAliases(KAFKA_ALIAS)
        .withCopyToContainer(Transferable.of(kafkaServerProperties()), serverPropertiesPath)
        .withExposedPorts(KAFKA_PORT)
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
        .withCommand("-c", kafkaCommand)
        .waitingFor(Wait.forListeningPort());
  }

  public void configureZookeeper(String zookeeperAddress) {
    if (bitnamiImage) {
      // bitnami images use different environment variable names
      this.withEnv("KAFKA_CFG_ZOOKEEPER_CONNECT", zookeeperAddress)
          // makes flush metrics available quickly
          .withEnv("KAFKA_CFG_LOG_FLUSH_INTERVAL_MESSAGES", "1")
          .withEnv("KAFKA_CFG_LOG_FLUSH_INTERVAL_MS", "100")
          // Removed in 3.5.1
          .withEnv("ALLOW_PLAINTEXT_LISTENER", "yes");
    } else {
      // this is more a to-do than a lack of support
      throw new IllegalStateException("not supported yet for non-bitnami images");
    }
    this.withExposedPorts(KAFKA_PORT)
        .waitingFor(
            Wait.forLogMessage(".*KafkaServer.*started \\(kafka.server.KafkaServer\\).*", 1));
  }

  private void configureKafkaConnect() {
    String connectPropertiesPath = basePath + "/config/connect-distributed.properties";
    this
        .withNetworkAliases(CONNECT_ALIAS)
        .withCopyToContainer(Transferable.of(connectWorkerProperties()), connectPropertiesPath)
        .withExposedPorts(CONNECT_PORT)
        .withCreateContainerCmdModifier(cmd -> cmd.withEntrypoint("/bin/sh"))
        .withCommand("-c", basePath + "/bin/connect-distributed.sh " + connectPropertiesPath)
        .waitingFor(
            Wait.forHttp("/connectors")
                .forPort(CONNECT_PORT)
                .withStartupTimeout(Duration.ofMinutes(5)));
  }

  private static String connectWorkerProperties() {
    return String.join(
        "\n",
        "bootstrap.servers=" + KAFKA_ALIAS + ":" + KAFKA_PORT,
        "group.id=connect-cluster",
        "key.converter=org.apache.kafka.connect.storage.StringConverter",
        "value.converter=org.apache.kafka.connect.storage.StringConverter",
        "key.converter.schemas.enable=false",
        "value.converter.schemas.enable=false",
        "offset.storage.topic=connect-offsets",
        "config.storage.topic=connect-configs",
        "status.storage.topic=connect-status",
        "offset.storage.replication.factor=1",
        "config.storage.replication.factor=1",
        "status.storage.replication.factor=1",
        "plugin.path=/opt/kafka/libs",
        "rest.host.name=0.0.0.0",
        "rest.advertised.host.name=" + CONNECT_ALIAS,
        "listeners=http://0.0.0.0:" + CONNECT_PORT);
  }

  private static String kafkaServerProperties() {
    return String.join(
        "\n",
        "process.roles=broker,controller",
        "node.id=1",
        "controller.quorum.voters=1@" + KAFKA_ALIAS + ":" + KAFKA_CONTROLLER_PORT,
        "listeners=PLAINTEXT://0.0.0.0:"
            + KAFKA_PORT
            + ",CONTROLLER://0.0.0.0:"
            + KAFKA_CONTROLLER_PORT,
        "listener.security.protocol.map=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT",
        "inter.broker.listener.name=PLAINTEXT",
        "controller.listener.names=CONTROLLER",
        "advertised.listeners=PLAINTEXT://" + KAFKA_ALIAS + ":" + KAFKA_PORT,
        "log.dirs=/tmp/kraft-combined-logs",
        "num.partitions=1",
        "offsets.topic.replication.factor=1",
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1",
        "group.initial.rebalance.delay.ms=0",
        "log.flush.interval.messages=1", // force flush quickly to get flush metrics
        "auto.create.topics.enable=true");
  }

}
