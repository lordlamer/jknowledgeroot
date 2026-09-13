#!/bin/bash
set -euo pipefail
# R13 is the pinned previous release-preparation state, not a published release.
baseline=7b85fa137c44b282441b47eb3d1d1f8c3f629e4d
root=$(CDPATH='' cd -- "$(dirname -- "$0")/.." && pwd)
destination="$root/target/recovery-baseline"
mkdir -- "$destination" # Never overwrite another checkout.
git -C "$root" archive "$baseline" | tar -xf - -C "$destination"
(cd -- "$destination" && sh ./mvnw -B --no-transfer-progress -DskipTests package && docker build --tag knowledgeroot:recovery-baseline .)
