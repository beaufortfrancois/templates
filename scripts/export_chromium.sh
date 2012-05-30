#!/bin/sh

# Exports required code to a chromium directory.

if [ $# == 0 ]; then
  echo "Exports required code to a chromium directory."
  echo
  echo "Usage: $0 /path/to/chromium/src"
  exit 1
fi

chromium="$1"
if [ ! -d "$chromium" ]; then
  echo "Error: no such directory $chromium"
  exit 1
fi

third_party="$chromium/third_party"
if [ ! -d "$third_party" ]; then
  echo "Error: no third_party directory, is $chromium chromium?"
  exit 1
fi

target="$chromium/third_party/handlebar"
mkdir -p "$target"
cp python/handlebar.py "$target"
