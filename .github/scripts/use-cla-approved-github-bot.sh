#!/bin/bash -e

git config user.name '${{ steps.app-token.outputs.app-slug }}[bot]'
git config user.email  '${{ steps.get-user-id.outputs.user-id }}+${{ steps.app-token.outputs.app-slug }}[bot]@users.noreply.github.com'
