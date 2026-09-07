#!/bin/bash
# Launch Codex pass H once the usage limit lifts (12:02 local 2026-09-07 = 02:02Z). Retries once on a further limit error.
cd /home/brock-gerand/uao-foundry
target=$(date -d '2026-09-07 12:05:00' +%s); now=$(date +%s); [ $now -lt $target ] && sleep $((target - now))
for attempt in 1 2; do
  codex exec --sandbox read-only < temp/codex-adr0007-pass-h-prompt.txt > temp/codex-uaofoundry-adr0007-ratification-pass-h-001.md 2> temp/codex-adr0007-pass-h-stderr.log
  rc=$?; echo "codex exit=$rc (attempt $attempt)" >> temp/codex-adr0007-pass-h-stderr.log
  if [ $rc -eq 0 ] && [ -s temp/codex-uaofoundry-adr0007-ratification-pass-h-001.md ]; then break; fi
  grep -q 'usage limit' temp/codex-adr0007-pass-h-stderr.log && sleep 1800
done
