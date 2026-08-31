# Harbor's own tasks, sourced by run.sh after its helpers so these can call setup
# and run_mvn. Keeping them here rather than in run.sh is what lets Harbor take a
# newer runner without a merge.

# The app with the archive rendered after the save instead of inside it, which is
# what harbor.archive.force-before-save=false buys: the bookmark is filed as soon
# as the page has been read, and the reader sees "Archiving…" until the copy lands.
#
# A task rather than a note in the README because the mode is only interesting while
# looking at it — the save that returns at once, the pending state, the copy arriving
# — and nobody should have to remember the variable's name to see that.
task_preview-deferred-archive() {
    export HARBOR_FORCE_ARCHIVE_BEFORE_SAVE=false
    task_run
}

project_usage() {
    cat <<'EOF'

Harbor's own:
  preview-deferred-archive
             Alias of run with HARBOR_FORCE_ARCHIVE_BEFORE_SAVE=false, so the
             archive is rendered after the save rather than inside it
EOF
}
