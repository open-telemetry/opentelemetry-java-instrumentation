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

# Add breaking changes section if it exists
if grep -q "### ⚠️ Breaking Changes" "$changelog_section"; then
  cat >> "$output_file" << 'EOF'

## 🚨 IMPORTANT: Breaking Changes

This release contains breaking changes. Please review the changes below:

EOF

  # Extract breaking changes section, format for release notes
  sed -n '/### ⚠️ Breaking Changes/,/^### /p' "$changelog_section" | sed '$d' | \
    perl -0pe 's/(?<!\n)\n *(?!\n)(?![-*] )(?![1-9]+\. )/ /g' >> "$output_file"

  echo -e "\n---\n" >> "$output_file"
fi

# the complex perl regex is needed because markdown docs render newlines as soft wraps
# while release notes render them as line breaks
perl -0pe 's/(?<!\n)\n *(?!\n)(?![-*] )(?![1-9]+\. )/ /g' "$changelog_section" >> "$output_file"
