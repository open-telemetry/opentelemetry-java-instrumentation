#!/bin/bash -e

# Format changelog section for GitHub release notes
# Usage: format-release-notes.sh <changelog_section_file> <output_file>

changelog_section="$1"
output_file="$2"

if [[ -z "$changelog_section" || -z "$output_file" ]]; then
  echo "Usage: format-release-notes.sh <changelog_section_file> <output_file>"
  exit 1
fi

if [[ ! -f "$changelog_section" ]]; then
  echo "Error: Changelog section file '$changelog_section' not found"
  exit 1
fi

{
  # Add breaking changes section if it exists
  if grep -q "### ⚠️ Breaking Changes" "$changelog_section"; then
    cat << 'EOF'

## 🚨 IMPORTANT: Breaking Changes

This release contains breaking changes. Please review the changes below:

EOF

    # Extract breaking changes section, format for release notes
    sed -n '/### ⚠️ Breaking Changes/,/^### /p' "$changelog_section" | sed '$d' | \
      perl -0pe 's/(?<!\n)\n *(?!\n)(?![-*] )(?![1-9]+\. )/ /g'

    echo -e "\n---\n"
  fi

  # Add deprecations section if it exists
  if grep -q "### 🚫 Deprecations" "$changelog_section"; then
    cat << 'EOF'

## 🚫 Deprecations

This release includes deprecations. Please review the changes below and plan for future updates:

EOF

    # Extract deprecations section, format for release notes
    sed -n '/### 🚫 Deprecations/,/^### /p' "$changelog_section" | sed '$d' | \
      perl -0pe 's/(?<!\n)\n *(?!\n)(?![-*] )(?![1-9]+\. )/ /g'

    echo -e "\n---\n"
  fi

  # the complex perl regex is needed because markdown docs render newlines as soft wraps
  # while release notes render them as line breaks
  perl -0pe 's/(?<!\n)\n *(?!\n)(?![-*] )(?![1-9]+\. )/ /g' "$changelog_section"
} > "$output_file"
