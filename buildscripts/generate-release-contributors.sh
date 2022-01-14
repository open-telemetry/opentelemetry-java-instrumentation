#!/bin/bash -e

last_version=$1
auth_token=$2

git log --pretty=format:"%s" $last_version..HEAD | grep -oE "\(#[0-9]{4,}\)" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/issues/{} | grep login | sed 's/.*"login": "\([^"]*\)".*/\1/' | sort -uf > pr-authors

git log --pretty=format:"%s" $last_version..HEAD | grep -oE "\(#[0-9]{4,}\)" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/issues/{}/comments | grep login | sed 's/.*"login": "\([^"]*\)".*/\1/' | sort -uf > pr-commenters

git log --pretty=format:"%s" $last_version..HEAD | grep -oE "\(#[0-9]{4,}\)" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/pulls/{}/comments | grep login | sed 's/.*"login": "\([^"]*\)".*/\1/' | sort -uf > pr-reviewers

git log --pretty=format:"%s" $last_version..HEAD | grep -oE "\(#[0-9]{4,}\)" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/pulls/{} | grep body | grep -oE "#[0-9]{4,}|issues/[0-9]{4,}" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/issues/{} | grep login | sed 's/.*"login": "\([^"]*\)".*/\1/' | sort -uf > issue-authors

git log --pretty=format:"%s" $last_version..HEAD | grep -oE "\(#[0-9]{4,}\)" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/pulls/{} | grep body | grep -oE "#[0-9]{4,}|issues/[0-9]{4,}" | grep -oE "[0-9]{4,}" | xargs -I{} curl -s -H "Authorization: token $auth_token" https://api.github.com/repos/open-telemetry/opentelemetry-java-instrumentation/issues/{}/comments | grep login | sed 's/.*"login": "\([^"]*\)".*/\1/' | sort -uf > issue-commenters

cat pr-* issue-* | sort -uf | grep -v "\[bot\]" | sed 's/^/@/'
