#!/bin/bash
# ############################################################################
# Report on the tags used in toxic/test files.
# ############################################################################

if [[ ! -d toxic/tests ]]; then
  echo "Must from from project with integraterd toxic tests (toxic/tests dir)"
  exit 1
fi

grep -h "^ *tags" toxic/tests/*.test | sed -e 's/^ *tags //' | sed -e 's|//.*$||' | tr -d '" ' | tr ',' '\n' | sort | uniq -c
