val stableVersion = "1.33.3"
val alphaVersion = "1.33.3-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
