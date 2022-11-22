plugins {
  id("otel.library-instrumentation")
}

// TODO: remove once spring-boot 3 gets released
repositories {
  mavenCentral()
  maven("https://repo.spring.io/milestone")
  mavenLocal()
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))

  compileOnly("org.springframework.kafka:spring-kafka:2.7.0")

  testImplementation(project(":instrumentation:spring:spring-kafka-2.7:testing"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))

  // 2.7.0 has a bug that makes decorating a Kafka Producer impossible
  testLibrary("org.springframework.kafka:spring-kafka:2.7.1")

  // TODO: remove once spring-boot 3 gets released
  if (latestDepTest) {
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.0.0-RC2")
    testImplementation("org.springframework.boot:spring-boot-starter:3.0.0-RC2")
  } else {
    testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.3")
    testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
  }
}

// spring 6 (which spring-kafka 3.+ uses) requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

// spring 6 uses slf4j 2.0
if (!latestDepTest) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
