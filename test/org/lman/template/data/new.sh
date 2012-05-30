#!/bin/sh

name="$1"

if [ -z "$name" ]; then
  echo "Usage: $0 name"
  exit 1
fi

touch "${name}.json"
touch "${name}.template"
touch "${name}.expected"

exec vim -p "${name}.json" "${name}.template" "${name}.expected"
