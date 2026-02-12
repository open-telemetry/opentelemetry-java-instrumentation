val stableVersion = "2.25.0"
val alphaVersion = "2.25.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
