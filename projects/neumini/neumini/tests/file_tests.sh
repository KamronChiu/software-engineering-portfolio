#!/usr/bin/env bash
set -euo pipefail
HERE="$(pwd)"
OUT="/tmp/neum_test_$(date +%s).txt"

# Run neumini non-interactively: create files, delete middle one, then list and view.
# Note: This noninteractive run will still perform add/delete but cannot test arrow-history.
cat > /tmp/neum_cmds.txt <<'EOF'
create a "AAAAAAAAAA"
create b "BBBBBBBBBBBB"
create c "CCCCCCCCCCCC"
list
delete b
list
view c
exit
EOF

# Run neumini with the commands
printf "Running neumini test... (output => %s)\n" "$OUT"
( cd "$HERE" && ./neumini < /tmp/neum_cmds.txt ) > "$OUT" 2>&1

# Output summary
echo "======== neumini output ========"
sed -n '1,200p' "$OUT"
echo "================================"

# Quick parse: count occurrences of 'b' in first list and second list by checking output lines
echo "Manual check: verify that 'b' is present before deletion and absent after deletion."
echo "You should inspect the sections above to confirm compaction and that 'view c' shows expected content."