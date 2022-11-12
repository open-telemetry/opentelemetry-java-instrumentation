#!/bin/bash -e

# this script merges release notes for $VERSION into CHANGELOG.md
# the release date for $VERSION should be available in $RELEASE_DATE
# and the release notes for $VERSION should be available in /tmp/changelog-section.md

if [[ $VERSION =~ ^[0-9]+\.[0-9]+\.0 ]]; then
  # this was not a patch release, so the version exists already in the CHANGELOG.md

  # update the release date
  sed -Ei "s/## Version $VERSION .*/## Version $VERSION ($RELEASE_DATE)/" CHANGELOG.md

  # the entries are copied over from the release branch to support workflows
  # where change log entries may be updated after preparing the release branch

  {
    # copy the portion above the release, up to and including the heading
    sed -n "0,/^## Version $VERSION /p" CHANGELOG.md
    # copy the release notes for $VERSION
    cat /tmp/changelog-section.md
    # copy the portion below the release
    sed -n "0,/^## Version $VERSION /d;0,/^## Version /{/^## Version/!d};p" CHANGELOG.md
  } > /tmp/CHANGELOG.md

  # update the real CHANGELOG.md
  cp /tmp/CHANGELOG.md CHANGELOG.md

else
  # this was a patch release, so the version does not exist already in the CHANGELOG.md

  {
    # copy the portion above the top-most release, not including the heading
    sed -n "0,/^## Version /{ /^## Version /!p }" CHANGELOG.md
    # add the heading
    echo "## Version $VERSION ($RELEASE_DATE)"
    # copy the release notes for $VERSION
    cat /tmp/changelog-section.md
    # copy the portion starting from the top-most release
    sed -n "/^## Version /,\$p" CHANGELOG.md
  } > /tmp/CHANGELOG.md

  # update the real CHANGELOG.md
  cp /tmp/CHANGELOG.md CHANGELOG.md
fi
