import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("com.alibaba.nacos")
    module.set("nacos-client")
    versions.set("[2.0.0,3.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.alibaba.nacos:nacos-client:2.0.0")
  latestDepTestLibrary("com.alibaba.nacos:nacos-client:2.+") // documented limitation

  testImplementation("org.testcontainers:testcontainers")
}

testing.suites {
  register<JvmTestSuite>("unitTests") {
    dependencies {
      implementation(project())
      implementation("com.alibaba.nacos:nacos-client:2.0.0")
    }
  }

  executions("test") {
    configureEach {
      jvmArgs("-Dotel.instrumentation.nacos-client.enabled=true")
      // keep the Nacos client's snapshot, cache and log files out of the user home directory
      val nacosClientDir = layout.buildDirectory.dir("nacos-client").get().asFile.absolutePath
      systemProperty("JM.SNAPSHOT.PATH", nacosClientDir)
      systemProperty("JM.LOG.PATH", nacosClientDir)
      if (otelProps.denyUnsafe) {
        // the protobuf and prometheus classes bundled with the Nacos client access sun.misc.Unsafe
        enabled = false
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
