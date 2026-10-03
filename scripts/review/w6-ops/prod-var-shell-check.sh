#!/usr/bin/env bash
# Review round 2 for PR #22 (W6-OPS): REBOOT_RECOVERY.md §3 defines PROD="docker compose -p bds-production -f ... --profile edge"
# and then runs `$PROD ps -aq`, `$PROD up -d ...`. bash word-splits an unquoted variable; zsh (the default login shell
# on macOS, and the shell of the production Mac) does not. This runs the same pattern with `echo` in both shells.
# Nothing touches Docker.
snippet='PROD="echo docker compose -p bds-production --profile edge"; $PROD ps -aq'
echo "SHELL of this user: $SHELL"
printf 'bash: '; bash -c "$snippet" 2>&1
printf 'zsh:  '; zsh -f -c "$snippet" 2>&1; echo "      (zsh exit $?)"
