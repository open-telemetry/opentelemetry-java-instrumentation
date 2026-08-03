import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

description = "opentelemetry-api shaded for internal javaagent usage"
group = "io.opentelemetry.javaagent"

val latestDeps = configurations.create("latestDeps") {
  isCanBeResolved = true
  isCanBeConsumed = false
}
val v1_10Deps = configurations.create("v1_10Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}
val v1_15Deps = configurations.create("v1_15Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}
val v1_27Deps = configurations.create("v1_27Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
}
val v1_31Deps = configurations.create("v1_31Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_32Deps = configurations.create("v1_32Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_37Deps = configurations.create("v1_37Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_38Deps = configurations.create("v1_38Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_40Deps = configurations.create("v1_40Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_42Deps = configurations.create("v1_42Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_47Deps = configurations.create("v1_47Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_50Deps = configurations.create("v1_50Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_56Deps = configurations.create("v1_56Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_57Deps = configurations.create("v1_57Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_59Deps = configurations.create("v1_59Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
val v1_63Deps = configurations.create("v1_63Deps") {
  isCanBeResolved = true
  isCanBeConsumed = false
  // exclude the bom added by dependencyManagement
  exclude("io.opentelemetry", "opentelemetry-bom")
  exclude("io.opentelemetry", "opentelemetry-bom-alpha")
}
// configuration for publishing the shadowed artifact
val v1_10 = configurations.create("v1_10") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_15 = configurations.create("v1_15") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_27 = configurations.create("v1_27") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_31 = configurations.create("v1_31") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_32 = configurations.create("v1_32") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_37 = configurations.create("v1_37") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_38 = configurations.create("v1_38") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_40 = configurations.create("v1_40") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_42 = configurations.create("v1_42") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_47 = configurations.create("v1_47") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_50 = configurations.create("v1_50") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_56 = configurations.create("v1_56") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_57 = configurations.create("v1_57") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_59 = configurations.create("v1_59") {
  isCanBeConsumed = true
  isCanBeResolved = false
}
val v1_63 = configurations.create("v1_63") {
  isCanBeConsumed = true
  isCanBeResolved = false
}

dependencies {
  latestDeps("io.opentelemetry:opentelemetry-api")

  listOf("opentelemetry-api", "opentelemetry-context").forEach {
    v1_10Deps("io.opentelemetry:$it") {
      version {
        strictly("1.10.0")
      }
    }
    v1_15Deps("io.opentelemetry:$it") {
      version {
        strictly("1.15.0")
      }
    }
    v1_27Deps("io.opentelemetry:$it") {
      version {
        strictly("1.27.0")
      }
    }
    v1_31Deps("io.opentelemetry:$it") {
      version {
        strictly("1.31.0")
      }
    }
    v1_32Deps("io.opentelemetry:$it") {
      version {
        strictly("1.32.0")
      }
    }
  }

  listOf("opentelemetry-extension-incubator").forEach {
    v1_31Deps("io.opentelemetry:$it") {
      version {
        strictly("1.31.0-alpha")
      }
    }
    v1_32Deps("io.opentelemetry:$it") {
      version {
        strictly("1.32.0-alpha")
      }
    }
  }

  listOf("opentelemetry-api-incubator").forEach {
    v1_37Deps("io.opentelemetry:$it") {
      version {
        strictly("1.37.0-alpha")
      }
    }
    v1_38Deps("io.opentelemetry:$it") {
      version {
        strictly("1.38.0-alpha")
      }
    }
    v1_40Deps("io.opentelemetry:$it") {
      version {
        strictly("1.40.0-alpha")
      }
    }
    v1_42Deps("io.opentelemetry:$it") {
      version {
        strictly("1.42.0-alpha")
      }
    }
    v1_47Deps("io.opentelemetry:$it") {
      version {
        strictly("1.47.0-alpha")
      }
    }
    v1_50Deps("io.opentelemetry:$it") {
      version {
        strictly("1.50.0-alpha")
      }
    }
    v1_56Deps("io.opentelemetry:$it") {
      version {
        strictly("1.56.0-alpha")
      }
    }
    v1_57Deps("io.opentelemetry:$it") {
      version {
        strictly("1.57.0-alpha")
      }
    }
    v1_59Deps("io.opentelemetry:$it") {
      version {
        strictly("1.59.0-alpha")
      }
    }
    v1_63Deps("io.opentelemetry:$it") {
      version {
        strictly("1.63.0-alpha")
      }
    }
  }
}

// OpenTelemetry API shaded so that it can be used in instrumentation of OpenTelemetry API itself,
// and then its usage can be unshaded after OpenTelemetry API is shaded
// (see more explanation in opentelemetry-api-1.0.gradle)
tasks {
  withType<ShadowJar>().configureEach {
    relocate("io.opentelemetry", "application.io.opentelemetry")
  }

  shadowJar {
    configurations = listOf(latestDeps)
  }

  val v1_10Shadow = register<ShadowJar>("v1_10Shadow") {
    configurations = listOf(v1_10Deps)
    archiveClassifier.set("v1_10")
  }
  val v1_15Shadow = register<ShadowJar>("v1_15Shadow") {
    configurations = listOf(v1_15Deps)
    archiveClassifier.set("v1_15")
  }
  val v1_27Shadow = register<ShadowJar>("v1_27Shadow") {
    configurations = listOf(v1_27Deps)
    archiveClassifier.set("v1_27")
  }
  val v1_31Shadow = register<ShadowJar>("v1_31Shadow") {
    configurations = listOf(v1_31Deps)
    archiveClassifier.set("v1_31")
  }
  val v1_32Shadow = register<ShadowJar>("v1_32Shadow") {
    configurations = listOf(v1_32Deps)
    archiveClassifier.set("v1_32")
  }
  val v1_37Shadow = register<ShadowJar>("v1_37Shadow") {
    configurations = listOf(v1_37Deps)
    archiveClassifier.set("v1_37")
  }
  val v1_38Shadow = register<ShadowJar>("v1_38Shadow") {
    configurations = listOf(v1_38Deps)
    archiveClassifier.set("v1_38")
  }
  val v1_40Shadow = register<ShadowJar>("v1_40Shadow") {
    configurations = listOf(v1_40Deps)
    archiveClassifier.set("v1_40")
  }
  val v1_42Shadow = register<ShadowJar>("v1_42Shadow") {
    configurations = listOf(v1_42Deps)
    archiveClassifier.set("v1_42")
  }
  val v1_47Shadow = register<ShadowJar>("v1_47Shadow") {
    configurations = listOf(v1_47Deps)
    archiveClassifier.set("v1_47")
  }
  val v1_50Shadow = register<ShadowJar>("v1_50Shadow") {
    configurations = listOf(v1_50Deps)
    archiveClassifier.set("v1_50")
  }
  val v1_56Shadow = register<ShadowJar>("v1_56Shadow") {
    configurations = listOf(v1_56Deps)
    archiveClassifier.set("v1_56")
  }
  val v1_57Shadow = register<ShadowJar>("v1_57Shadow") {
    configurations = listOf(v1_57Deps)
    archiveClassifier.set("v1_57")
  }
  val v1_59Shadow = register<ShadowJar>("v1_59Shadow") {
    configurations = listOf(v1_59Deps)
    archiveClassifier.set("v1_59")
  }
  val v1_63Shadow = register<ShadowJar>("v1_63Shadow") {
    configurations = listOf(v1_63Deps)
    archiveClassifier.set("v1_63")
  }

  artifacts {
    add(v1_10.name, v1_10Shadow)
    add(v1_15.name, v1_15Shadow)
    add(v1_27.name, v1_27Shadow)
    add(v1_31.name, v1_31Shadow)
    add(v1_32.name, v1_32Shadow)
    add(v1_37.name, v1_37Shadow)
    add(v1_38.name, v1_38Shadow)
    add(v1_40.name, v1_40Shadow)
    add(v1_42.name, v1_42Shadow)
    add(v1_47.name, v1_47Shadow)
    add(v1_50.name, v1_50Shadow)
    add(v1_56.name, v1_56Shadow)
    add(v1_57.name, v1_57Shadow)
    add(v1_59.name, v1_59Shadow)
    add(v1_63.name, v1_63Shadow)
  }
}
