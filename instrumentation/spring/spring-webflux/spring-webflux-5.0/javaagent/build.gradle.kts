plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    name.set("webflux_5.0.0+_with_netty_0.8.0")
    group.set("org.springframework")
    module.set("spring-webflux")
    versions.set("[5.0.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor.netty:reactor-netty:0.8.0.RELEASE")
  }

  pass {
    name.set("webflux_5.0.0_with_ipc_0.7.0")
    group.set("org.springframework")
    module.set("spring-webflux")
    versions.set("[5.0.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")
  }

  pass {
    name.set("netty_0.8.0+_with_spring-webflux:5.1.0")
    group.set("io.projectreactor.netty")
    module.set("reactor-netty")
    versions.set("[0.8.0.RELEASE,)")
    extraDependency("org.springframework:spring-webflux:5.1.0.RELEASE")
  }

  pass {
    name.set("ipc_0.7.0+_with_spring-webflux:5.0.0")
    group.set("io.projectreactor.ipc")
    module.set("reactor-netty")
    versions.set("[0.7.0.RELEASE,)")
    extraDependency("org.springframework:spring-webflux:5.0.0.RELEASE")
  }
}

dependencies {
  implementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:library"))
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("org.springframework:spring-webflux:5.0.0.RELEASE")
  compileOnly("io.projectreactor.ipc:reactor-netty:0.7.0.RELEASE")

  // this is needed to pick up SpringCoreIgnoredTypesConfigurer
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-netty:reactor-netty-1.0:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-webflux:spring-webflux-5.3:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-webflux:2.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-test:2.0.0.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-reactor-netty:2.0.0.RELEASE")
  testImplementation("org.spockframework:spock-spring:2.4-M1-groovy-4.0")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.spring-webflux.experimental-span-attributes=true")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=false")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

if (latestDepTest) {
  // spring 6 requires java 17
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
} else {
  // spring 5 requires old logback (and therefore also old slf4j)
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
