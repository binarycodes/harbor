#!/usr/bin/env bash
#
# Task runner for the Harbor app.
#
# Every task pins JAVA_HOME to a JDK 21 because a bare `mvn` on this machine
# picks JDK 25, under which Lombok silently fails to generate getters/setters
# and the build dies with bogus "cannot find symbol" errors.
#
# Usage: ./run.sh <task>   (run with no task, or `help`, to list tasks)

set -euo pipefail

cd "$(dirname "$0")"

readonly BUNDLE_DIR="src/main/bundles"
readonly DEV_COMPOSE_FILE="environment/dev/compose.yaml"
readonly STYLES_CSS="src/main/resources/META-INF/resources/styles.css"

# Resolve a JDK 21 from SDKMAN; fall back to whatever JAVA_HOME is already set.
resolve_java_home() {
    local jdk21
    jdk21=$(ls -d "${HOME}"/.sdkman/candidates/java/21* 2>/dev/null | head -1 || true)
    if [[ -n "${jdk21}" ]]; then
        export JAVA_HOME="${jdk21}"
    elif [[ -z "${JAVA_HOME:-}" ]]; then
        echo "No JDK 21 found under ~/.sdkman and JAVA_HOME is unset." >&2
        exit 1
    fi
    echo "Using JAVA_HOME=${JAVA_HOME}"
}

# Testcontainers finds the daemon through DOCKER_HOST or /var/run/docker.sock. The
# docker CLI instead reads its own "context", so on Colima and Rancher Desktop the CLI
# works while Testcontainers reports "Could not find a valid Docker environment" — the
# socket is under the user's home, which docker-java never looks at. Take the endpoint
# from the context the CLI is actually using.
#
# The socket override is for Ryuk, the cleanup sidecar: it mounts the socket from
# inside the VM, where the path is /var/run/docker.sock regardless of where the host
# sees it.
resolve_docker_host() {
    if [[ -n "${DOCKER_HOST:-}" ]]; then
        return
    fi
    if [[ -S /var/run/docker.sock ]]; then
        return
    fi
    local endpoint
    endpoint=$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)
    if [[ -n "${endpoint}" && "${endpoint}" != "unix:///var/run/docker.sock" ]]; then
        export DOCKER_HOST="${endpoint}"
        export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"
        echo "Using DOCKER_HOST=${DOCKER_HOST}"
    fi
}

# Every mvn build must carry the deployed commit SHA — the enforcer plugin fails
# the build without a valid build.commit. Resolve it once from the working tree
# and pass it to every mvn invocation. A non-repo checkout is a hard error by
# design: an unidentifiable build should never be produced.
run_mvn() {
    local commit
    if ! commit=$(git rev-parse HEAD 2>/dev/null); then
        echo "Cannot resolve the git commit (not a git repository?)." >&2
        echo "build.commit is mandatory; refusing to build." >&2
        exit 1
    fi
    mvn "$@" -Dbuild.commit="${commit}"
}

# Bump styles.css mtime. styles.css is only @import statements; editing an
# imported partial (colors.css, grid.css, ...) leaves styles.css unchanged, so
# the browser serves a 304 and keeps the stale partial. Touching the entry file
# busts that cache.
touch_styles() {
    touch "${STYLES_CSS}"
    echo "Touched ${STYLES_CSS}"
}

# Remove the cached frontend bundles. Vaadin's dev server reuses an existing
# bundle and logs "a development mode bundle build is not needed", so a changed
# @CssImport(themeFor=...) is ignored until both bundles are gone. Deleting only
# dev.bundle is not enough — a leftover prod.bundle is still treated as valid.
clear_bundles() {
    rm -rf "${BUNDLE_DIR}/dev.bundle" "${BUNDLE_DIR}/prod.bundle" target/dev-bundle
    echo "Cleared cached frontend bundles"
}

task_compile() {
    resolve_java_home
    run_mvn -o -q compile
    echo "Compiled. spring-boot-devtools will hot-restart a running app."
}

# Force a clean frontend rebuild after a @CssImport / @JsModule change: drop the
# cached bundles, touch styles.css, then compile so devtools rebuilds the bundle.
task_bundle() {
    resolve_java_home
    clear_bundles
    touch_styles
    run_mvn -o -q compile
    echo "Bundle cleared and recompiled. Reload the browser to pick up the new bundle."
}

task_styles() {
    touch_styles
}

# Docker renamed compose from a script to a subcommand and both are still in the
# wild, so find whichever this machine has rather than guessing.
compose() {
    if docker compose version >/dev/null 2>&1; then
        docker compose -f "${DEV_COMPOSE_FILE}" "$@"
    elif command -v docker-compose >/dev/null 2>&1; then
        docker-compose -f "${DEV_COMPOSE_FILE}" "$@"
    else
        echo "No Compose found. Install the Docker Compose plugin (docker compose)" >&2
        echo "or the standalone docker-compose, then retry." >&2
        exit 1
    fi
}

# The whole development stack, because Harbor needs all of it: the library lives in
# Postgres, a page that cannot be archived is not saved, and login is mandatory on
# every route. Which containers that means is compose's to decide from
# environment/dev/compose.yaml, so adding a service there needs no change here.
#
# --wait rather than plain -d: every service declares a healthcheck, and the app
# starting before them is what turns a slow container into a confusing connection or
# discovery error at startup.
task_env() {
    local action="${1:-up}"
    case "${action}" in
        up)    compose up -d --wait && echo "Development stack is up. Harbor needs no configuration to reach it." ;;
        down)  compose stop && echo "Development stack stopped; its data is kept." ;;
        logs)  compose logs -f ;;
        reset) compose down -v && echo "Development stack stopped and its data thrown away." ;;
        *)
            echo "Unknown env action: ${action} (expected up, down, logs or reset)" >&2
            exit 1
            ;;
    esac
}

# Fetch dependencies. Every other task builds offline, which is what makes a
# newly added dependency fail with a resolution error rather than downloading it.
task_deps() {
    resolve_java_home
    run_mvn --batch-mode --no-transfer-progress dependency:resolve
}

task_test() {
    resolve_java_home
    resolve_docker_host
    # JaCoCo enforces an 80% line-coverage gate on the */service packages.
    run_mvn -o test "$@"
}

task_run() {
    resolve_java_home
    run_mvn -o spring-boot:run
}

# Same as `run`, but named for the Claude Code preview pane, which launches the
# app through this task (see .claude/launch.json) so the preview goes through
# run.sh — pinned JDK 21 and the offline/bundle gotchas — like every other task,
# instead of invoking mvn directly.
task_preview() {
    task_run
}

# Unit tests plus the Playwright integration tests, against a production build.
#
# -Pit is what puts the app in production mode; without it the ITs open a
# dev-mode page and find an empty screen. The bundles are cleared first because a
# dev.bundle left behind by `./run.sh run` makes the frontend build decide "a
# production mode bundle build is not needed" and the app then serves a bundle
# that fails to boot — the same trap `clean` exists for, and `mvn clean` alone
# does not clear it since the bundles live under src/.
task_verify() {
    resolve_java_home
    resolve_docker_host
    clear_bundles
    run_mvn clean verify -Pit "$@"
}

# Full production build.
task_package() {
    resolve_java_home
    run_mvn clean package "$@"
}

task_clean() {
    resolve_java_home
    clear_bundles
    mvn -o clean
}

usage() {
    cat <<'EOF'
Usage: ./run.sh <task>

Tasks:
  env [act]  Everything in environment/dev/compose.yaml: up (default), down, logs,
             reset (throws the data away)
  deps       Download newly added dependencies (every other task builds offline)
  compile    Compile sources (triggers a devtools hot-restart of a running app)
  bundle     Clear cached frontend bundles + touch styles.css + recompile
             (use after changing a @CssImport themeFor / @JsModule)
  styles     Touch styles.css so the browser reloads @imported CSS partials
  test       Run the unit tests (enforces the JaCoCo coverage gate)
  verify     Unit tests + Playwright integration tests (mvn clean verify)
  run        Start the app with spring-boot:run (dev mode)
  preview    Alias of run, launched by the Claude Code preview pane
  package    Full production build (mvn clean package)
  clean      mvn clean + remove cached bundles
  help       Show this message
EOF
}

main() {
    local task="${1:-help}"
    case "${task}" in
        compile) task_compile ;;
        bundle)  task_bundle ;;
        styles)  task_styles ;;
        env)     task_env "${2:-up}" ;;
        deps)    task_deps ;;
        test)    task_test "${@:2}" ;;
        verify)  task_verify "${@:2}" ;;
        run)     task_run ;;
        preview) task_preview ;;
        package) task_package "${@:2}" ;;
        clean)   task_clean ;;
        help|-h|--help) usage ;;
        *)
            echo "Unknown task: ${task}" >&2
            echo >&2
            usage >&2
            exit 1
            ;;
    esac
}

main "$@"
