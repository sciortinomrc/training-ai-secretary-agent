#!/usr/bin/env bash
# Opens the calendar agent in tmux, side by side:
#   left:  the chat between you and the agent
#   right: the trace of every step between you, the agent and the LLM
# Type 'exit' in the chat to close both panes.
# Extra options go to the agent, for example: ./run-side-by-side.sh -Dcalendar.file=/tmp/test.json
set -euo pipefail
cd "$(dirname "$0")"

TRACE_FILE="trace.log"
SESSION="ai-calendar"

if tmux has-session -t "$SESSION" 2>/dev/null; then
  echo "The side-by-side view is already open. Joining it."
else
  : > "$TRACE_FILE"
  echo "Compiling..."
  mvn -q compile
  tmux new-session -d -s "$SESSION" -x "$(tput cols)" -y "$(tput lines)" \
    "mvn -q exec:java -Dtrace.file=$TRACE_FILE $*; tmux kill-session -t $SESSION"
  tmux split-window -h -t "$SESSION" "tail -f $TRACE_FILE"
  tmux select-pane -t "$SESSION:0.0" -T "You ⇄ Agent"
  tmux select-pane -t "$SESSION:0.1" -T "Agent ⇄ LLM"
  tmux set-option -t "$SESSION" pane-border-status top >/dev/null
  tmux select-pane -t "$SESSION:0.0"
fi

if [ -n "${TMUX:-}" ]; then
  tmux switch-client -t "$SESSION"
else
  tmux attach -t "$SESSION"
fi
