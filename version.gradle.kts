val stableVersion = "2.1.0-SNAPSHOT"
val alphaVersion = "2.1.0-alpha-SNAPSHOT"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
