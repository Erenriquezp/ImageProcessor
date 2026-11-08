#!/bin/sh
# Activa los hooks versionados en tools/hooks (solo cambia la config local del repo).
set -e
root=$(git rev-parse --show-toplevel)
cd "$root"
chmod +x tools/hooks/commit-msg tools/hooks/pre-commit
git config core.hooksPath tools/hooks
echo "Hooks activados (core.hooksPath = tools/hooks)."
echo "Para desactivarlos: git config --unset core.hooksPath"
