import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.apache.logging.log4j")
    module.set("log4j-core")
    versions.set("[2.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  testInstrumentation(project(":instrumentation:log4j:log4j-appender-1.2:javaagent"))

  library("org.apache.logging.log4j:log4j-core:2.0")

  implementation(project(":instrumentation:log4j:log4j-appender-2.17:library"))

  testImplementation(project(":instrumentation:log4j:log4j-appender-2.17:testing"))

  if (otelProps.testLatestDeps) {
    // this dependency is needed for the slf4j->log4j test
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.+")
    testCompileOnly("biz.aQute.bnd:biz.aQute.bnd.annotation:7.0.0")
    testCompileOnly("com.google.errorprone:error_prone_annotations")
  } else {
    // log4j 2.17 doesn't have an slf4j2 bridge
    testImplementation("org.apache.logging.log4j:log4j-slf4j-impl:2.17.0")
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.7.36")
      }
    }
  }

  // this is needed for the async logging test
  testLibrary("com.lmax:disruptor:3.4.2")
}

testing.suites {
  listOf("2.0", "2.8.2", "2.9.0", "2.17.0").forEach { log4jVersion ->
    register<JvmTestSuite>("mapMessage${log4jVersion.replace(".", "_")}Test") {
      sources.java.srcDir("src/mapMessageCompatibilityTest/java")
      dependencies {
        implementation("org.apache.logging.log4j:log4j-core:$log4jVersion") {
          version { strictly(log4jVersion) }
        }
        implementation("org.apache.logging.log4j:log4j-api:$log4jVersion") {
          version { strictly(log4jVersion) }
        }
      }
    }
  }

  executions("mapMessage2_0Test", "mapMessage2_8_2Test", "mapMessage2_9_0Test", "mapMessage2_17_0Test") {
    configureEach {
      systemProperty("otel.instrumentation.common.logging.structured-attributes.included", "keep-*")
      systemProperty("otel.instrumentation.common.logging.structured-attributes.excluded", "keep-secret")
      systemProperty("test.mapMessageUsesGetData", name == "mapMessage2_0Test" || name == "mapMessage2_8_2Test")
    }
  }

  executions("test") {
    configureDefault {
      jvmArgs(
        "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.included=key1,key2,exact,prefix.*,single?,excluded*,otel.event.name",
        "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
        "-Dotel.instrumentation.common.logging.structured-attributes.included=key1,key2,order-*,user-?",
        "-Dotel.instrumentation.common.logging.structured-attributes.excluded=*-secret",
      )
    }
    configureEach {
      // TODO run tests both with and without experimental log attributes
      jvmArgs("-Dotel.instrumentation.log4j-appender.experimental-log-attributes=true")
      jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-code-attributes=true")
      jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-marker-attribute=true")

      if (otelProps.denyUnsafe) {
        enabled = false
      }
    }
    register("Async") {
      jvmArgs(
        "-DLog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
        "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.included=key1,key2,exact,prefix.*,single?,excluded*,otel.event.name",
        "-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret,excluded*",
        "-Dotel.instrumentation.common.logging.structured-attributes.included=key1,key2,order-*,user-?",
        "-Dotel.instrumentation.common.logging.structured-attributes.excluded=*-secret",
      )
    }
    register("LegacyMdcAttributes") {
      filter.includeTestsMatching("*Log4jMdcSelectorTest")

      // the deprecated setting matches keys literally, so "*" alongside another entry does not
      // capture every key
      jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.capture-mdc-attributes=*,legacy")
      systemProperty("testMdcConfiguration", "legacy")
    }
    register("MdcAttributeExclusionsOnly") {
      filter.includeTestsMatching("*Log4jMdcSelectorTest")

      jvmArgs("-Dotel.instrumentation.log4j-appender.experimental.mdc-attributes.excluded=prefix.secret")
      systemProperty("testMdcConfiguration", "exclude-only")
    }
    register("StructuredAttributesExcludeOnly") {
      filter.includeTestsMatching("*Log4jMapMessageSelectorTest")

      jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=*-secret")
      systemProperty("testMapMessageConfiguration", "exclude-only")
    }
    register("StructuredAttributesExcludedAll") {
      filter.includeTestsMatching("*Log4jMapMessageSelectorTest")

      jvmArgs("-Dotel.instrumentation.common.logging.structured-attributes.excluded=*")
      systemProperty("testMapMessageConfiguration", "none")
    }
  }
}

configurations {
  testImplementation {
    // this is needed for the slf4j->log4j test
    exclude("ch.qos.logback", "logback-classic")
  }
}

tasks.check {
  dependsOn(testing.suites)
}
