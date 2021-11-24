# Upgrading the gradle wrappers

Set `GRADLE_VERSION` to the version of gradle.

Set `GRADLE_VERSION_CHECKSUM` to the "Binary-only (-bin) ZIP Checksum" for that version from https://gradle.org/release-checksums/.

Then run:

```
for dir in . \
           benchmark-overhead \
           examples/distro \
           examples/extension \
           smoke-tests/images/fake-backend \
           smoke-tests/images/grpc \
           smoke-tests/images/servlet \
           smoke-tests/images/play \
           smoke-tests/images/spring-boot
do
  (cd $dir && ./gradlew wrapper --gradle-version $GRADLE_VERSION \
                  --gradle-distribution-sha256-sum=$GRADLE_VERSION_CHECKSUM)
done
```
