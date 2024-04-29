package io.opentelemetry.smoketest;

import org.junit.jupiter.api.condition.DisabledInNativeImage;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@DisabledInNativeImage
public class JvmMongodbSpringStarterSmokeTest extends AbstractMongodbSpringStarterSmokeTest {

  @Container
  @ServiceConnection
  static MongoDBContainer container = new MongoDBContainer("mongo:latest");

}
