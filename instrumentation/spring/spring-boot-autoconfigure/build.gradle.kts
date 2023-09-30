plugins {
  id("otel.library-instrumentation")
}

// Name the Spring Boot modules in accordance with https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.custom-starter
base.archivesName.set("opentelemetry-spring-boot")
group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")
  implementation("javax.validation:validation-api")

  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))
  implementation(project(":instrumentation:spring:spring-kafka-2.7:library"))
  implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-5.3:library"))
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-6.0:library"))
  compileOnly("javax.servlet:javax.servlet-api:3.1.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
  implementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:library"))
  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))
  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))
  compileOnly("org.apache.logging.log4j:log4j-core:2.17.0")
  implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
  compileOnly("ch.qos.logback:logback-classic:1.0.0")

  library("org.springframework.kafka:spring-kafka:2.9.0")
  library("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  library("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry:opentelemetry-extension-annotations")
  compileOnly("io.opentelemetry:opentelemetry-extension-trace-propagators")
  compileOnly("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
  compileOnly("io.opentelemetry:opentelemetry-exporter-logging")
  compileOnly("io.opentelemetry:opentelemetry-exporter-jaeger")
  compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
  compileOnly("io.opentelemetry:opentelemetry-exporter-zipkin")
  compileOnly(project(":instrumentation-annotations"))

  compileOnly(project(":instrumentation:resources:library"))
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

  testLibrary("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
  testImplementation("org.testcontainers:kafka")
  testImplementation("javax.servlet:javax.servlet-api:3.1.0")
  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation(project(":instrumentation:resources:library"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
  testImplementation("io.opentelemetry:opentelemetry-exporter-jaeger")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  testImplementation("io.opentelemetry:opentelemetry-exporter-zipkin")
  testImplementation(project(":instrumentation-annotations"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 (spring boot 3) requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

testing {
  suites {
    val testLogbackAppender by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        implementation(project(":testing-common"))
        implementation("io.opentelemetry:opentelemetry-sdk")
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")

        implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
        // using the same versions as in the spring-boot-autoconfigure
        implementation("ch.qos.logback:logback-classic") {
          version {
            strictly("1.2.11")
          }
        }
        implementation("org.slf4j:slf4j-api") {
          version {
            strictly("1.7.32")
          }
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }

  compileTestJava {
    options.compilerArgs.add("-parameters")
  }

  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", latestDepTest)

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    // disable tests on openj9 18 because they often crash JIT compiler
    val testJavaVersion = gradle.startParameter.projectProperties["testJavaVersion"]?.let(JavaVersion::toVersion)
    val testOnOpenJ9 = gradle.startParameter.projectProperties["testJavaVM"]?.run { this == "openj9" }
      ?: false
    if (testOnOpenJ9 && testJavaVersion?.majorVersion == "18") {
      enabled = false
    }
  }
}
