# Copyright 2012 Benjamin Kalman
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/bin/bash

copyright_start="Copyright 2012 Benjamin Kalman"

copyright="${copyright_start}

Licensed under the Apache License, Version 2.0 (the \"License\");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an \"AS IS\" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License."

if [ $# == 0 ]; then
  echo "Puts licenses on files if they don't already have them."
  echo "The comment string is determined by the file type."
  echo
  echo "Usage: $0 files..."
  exit 1
fi

hasExtension() {
  file="$1"
  extension="$2"
  if echo "$file" | grep -Eq "\\.${extension}$"; then
    return 0
  else
    return 1
  fi
}

applyComment() {
  file="$1"
  string="$2"
  if hasExtension "$file" py; then
    sep="#"
  elif hasExtension "$file" js; then
    sep="//"
  elif hasExtension "$file" coffee; then
    sep="#"
  elif hasExtension "$file" java; then
    sep="//"
  else
    echo "Unrecognized extension for $f"
    return 1
  fi
  echo "$string" | sed "s:^:${sep} :g" | sed -E "s: +$::g"
}

hasCopyright() {
  file="$1"
  first_line="$(head -n1 "$file")"
  expected_comment="$(applyComment "$file" "$copyright_start")"
  if [ "$first_line" == "$expected_comment" ]; then
    return 0
  else
    return 1
  fi
}

if [ -e .tmp ]; then
  echo ".tmp already exists"
  exit 1
fi

mkdir .tmp

for f in "$@"; do
  if [ ! -f "$f" ]; then
    echo "Warning: $f is not file"
    continue
  fi

  if hasCopyright "$f"; then
    echo "Skipping: $f already has a copyright header"
  else
    echo "Applying: $f"
    mv "$f" .tmp
    applyComment "$f" "$copyright" > "$f"
    echo >> "$f"
    cat .tmp/* >> "$f"
    rm .tmp/*
  fi
done

rmdir .tmp
