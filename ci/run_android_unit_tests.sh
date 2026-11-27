#!/usr/bin/env bash
set -euo pipefail

args=("$@")
for attempt in 1 2 3; do
  log="${RUNNER_TEMP:-/tmp}/dichgame-android-unit-tests-${attempt}.log"
  echo "Android unit tests: attempt ${attempt}/3 (Robolectric Maven Central mirror configured in app/build.gradle.kts)"
  if gradle :app:testDebugUnitTest "${args[@]}" --no-daemon --stacktrace 2>&1 | tee "$log"; then
    exit 0
  fi

  # Retry only the known transient Robolectric artifact-fetch failure.
  # Assertion failures and application test failures must fail immediately.
  if ! grep -q "MavenArtifactFetcher" "$log"; then
    echo "::error::Android unit tests failed for a reason other than Robolectric Maven artifact download; not retrying."
    exit 1
  fi

  if [ "$attempt" -eq 3 ]; then
    echo "::error::Robolectric could not download its Android runtime from Maven after 3 attempts."
    exit 1
  fi
  echo "::warning::Robolectric Maven download failed; retrying after backoff."
  sleep $((attempt * 12))
done
