#!/bin/bash -e

# shellcheck disable=SC2016
# shellcheck disable=SC2086

from_version=$1
to_version=$2

# get the date of the first commit on main that wasn't in the from_version
from=$(git log --reverse --pretty=format:"%cI" $from_version..HEAD | head -1)

# get the last commit on main that was in the to_version
to=$(git merge-base HEAD $to_version | xargs git log -1 --pretty=format:"%cI")

contributors1=$(gh api graphql --paginate -F q="repo:open-telemetry/opentelemetry-java-instrumentation is:pr base:main is:merged merged:$from..$to" -f query='
query($q: String!, $endCursor: String) {
  search(query: $q, type: ISSUE, first: 100, after: $endCursor) {
    edges {
      node {
        ... on PullRequest {
          author { login }
          reviews(first: 100) {
            nodes {
              author { login }
            }
          }
          comments(first: 100) {
            nodes {
              author { login }
            }
          }
          closingIssuesReferences(first: 100) {
            nodes {
              author { login }
            }
          }
        }
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}' --jq '.data.search.edges.[].node.author.login,
       .data.search.edges.[].node.reviews.nodes.[].author.login,
       .data.search.edges.[].node.comments.nodes.[].author.login,
       .data.search.edges.[].node.closingIssuesReferences.nodes.[].author.login')

# this query captures authors of issues which have had PRs in the current range reference the issue
# but not necessarily through closingIssuesReferences (e.g. addressing just a part of an issue)
contributors2=$(gh api graphql --paginate -F q="repo:open-telemetry/opentelemetry-java-instrumentation is:pr base:main is:merged merged:$from..$to" -f query='
query($q: String!, $endCursor: String) {
  search(query: $q, type: ISSUE, first: 100, after: $endCursor) {
    edges {
      node {
        ... on PullRequest {
          body
        }
      }
    }
    pageInfo {
      hasNextPage
      endCursor
    }
  }
}
' --jq '.data.search.edges.[].node.body' \
  | grep -oE "#[0-9]{4,}|issues/[0-9]{4,}" \
  | grep -oE "[0-9]{4,}" \
  | xargs -I{} gh issue view {} --json 'author,url' --jq '[.author.login,.url]' \
  | grep -v '/pull/' \
  | sed 's/^\["//' \
  | sed 's/".*//')

echo $contributors1 $contributors2 \
  | sed 's/ /\n/g' \
  | sort -uf \
  | grep -v linux-foundation-easycla \
  | grep -v github-actions \
  | grep -v dependabot \
  | sed 's/^/@/'
