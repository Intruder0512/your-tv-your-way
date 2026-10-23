#!/usr/bin/env bash
# Rebuild the launcher after editing launcher/app/src/main/res/values/config.xml and install it on the TV.
# Run from Git Bash in the repo folder:  work/build-and-install.sh [tv ip]
# Needs USB debugging switched on again on the TV (Settings > Device Preferences > Developer options).
set -euo pipefail
cd "$(dirname "$0")/.."
# work/local.env (not committed) can hold TV_HOST=<tv ip>, so the address need not be typed each time.
if [ -f work/local.env ]; then . work/local.env; fi
export TV_HOST="${1:-${TV_HOST:?give the TV's IP address: $0 <tv ip>}}"
TC="$HOME/.cleanyourtv/toolchain"
SHORT_TMP="$HOME/.cleanyourtv/tmp"; mkdir -p "$SHORT_TMP"
# Windows: JDK 17's loopback socket lives in the temp folder and fails on a long temp path.
export TMP="$(cd "$SHORT_TMP" && pwd -W)" TEMP="$(cd "$SHORT_TMP" && pwd -W)"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=$TMP -Djava.io.tmpdir=$TMP"
export JAVA_HOME="$(cd "$TC"/jdk/* && pwd -W)"
export ANDROID_HOME="$(cd "$TC/sdk" && pwd -W)"
export PATH="$(cd "$TC"/jdk/* && pwd)/bin:$PATH"
status=0
launcher/build-and-install.sh || status=$?
# The install script leaves a Gradle daemon running, and on Windows it keeps a lock on the last build's files:
# the next build then fails with "being used by another process". Stop it.
(cd launcher && ./gradlew --stop >/dev/null 2>&1) || true
exit $status
