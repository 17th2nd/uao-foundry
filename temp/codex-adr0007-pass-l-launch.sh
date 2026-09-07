#!/bin/bash
cd /home/brock-gerand/uao-foundry
for attempt in 1 2 3; do
  codex exec --sandbox read-only < temp/codex-adr0007-pass-l-prompt.txt > temp/codex-uaofoundry-adr0007-ratification-pass-l-001.md 2> temp/codex-adr0007-pass-l-stderr.log
  rc=$?; echo "codex exit=$rc (attempt $attempt)" >> temp/codex-adr0007-pass-l-stderr.log
  if [ $rc -eq 0 ] && [ -s temp/codex-uaofoundry-adr0007-ratification-pass-l-001.md ]; then echo "codex done" >> temp/codex-adr0007-pass-l-stderr.log; break; fi
  grep -q 'usage limit' temp/codex-adr0007-pass-l-stderr.log && sleep 3600 || break
done
echo "launcher done" >> temp/codex-adr0007-pass-l-stderr.log
