#!/bin/bash
# ============================================================================
#  DEMO: WITH Circuit Breaker — Service Survives
# ============================================================================

SUPPLIER="http://localhost:8080"
STORE="http://localhost:8082"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

banner() { echo -e "\n${CYAN}════════════════════════════════════════════════════════════════${NC}"; echo -e "${BOLD}  $1${NC}"; echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}\n"; }
step()   { echo -e "${YELLOW}▶ $1${NC}"; }
info()   { echo -e "${GREEN}  $1${NC}"; }
fail()   { echo -e "${RED}  $1${NC}"; }
pause()  { echo ""; read -rp "  [Press ENTER to continue] "; echo ""; }

cb_state() {
  curl -s "$STORE/store/health" | jq -r '.circuitBreaker.state'
}

show_cb() {
  step "Circuit Breaker state:"
  curl -s "$STORE/store/health" | jq '.circuitBreaker'
}

timed_request() {
  local LABEL="$1"
  START=$(date +%s%N)
  RESPONSE=$(curl -s --max-time 10 "$STORE/store/products")
  END=$(date +%s%N)
  ELAPSED=$(( (END - START) / 1000000 ))

  IS_FALLBACK=$(echo "$RESPONSE" | jq -r '.fallback // false' 2>/dev/null)

  if [ "$IS_FALLBACK" = "true" ]; then
    REASON=$(echo "$RESPONSE" | jq -r '.reason' 2>/dev/null)
    fail "$LABEL → FALLBACK in ${ELAPSED}ms — $REASON"
  else
    FIRST=$(echo "$RESPONSE" | jq -r '.[0].name' 2>/dev/null)
    info "$LABEL → OK in ${ELAPSED}ms — $FIRST, ..."
  fi
}

# --------------------------------------------------------------------------
banner "DEMO: Store WITH Circuit Breaker (Resilience4j)"
echo -e "Same scenario as before: the supplier becomes slow."
echo -e "But this store service has a ${GREEN}Circuit Breaker${NC} protecting it."
echo ""
echo -e "  Store Service (CB):  ${BOLD}$STORE${NC}   (Tomcat maxThreads = 5, readTimeout = 3s)"
echo -e "  Supplier Service:    ${BOLD}$SUPPLIER${NC}"
echo ""
echo -e "  CB config: slidingWindow=5, minCalls=3, failureThreshold=50%"
echo -e "             waitInOpenState=15s, halfOpenPermits=2"
pause

# --------------------------------------------------------------------------
banner "PHASE 1: Normal Operation — Circuit is CLOSED"

step "Resetting supplier to HEALTHY mode..."
curl -s -X POST "$SUPPLIER/api/simulate/healthy" | jq .
echo ""

step "Calling store → supplier (should work fine)..."
for i in 1 2 3; do
  timed_request "Request $i"
done
echo ""
show_cb
info "Circuit is CLOSED. All calls succeed normally."
pause

# --------------------------------------------------------------------------
banner "PHASE 2: Supplier Becomes SLOW (30s delay)"

step "Switching supplier to SLOW mode..."
curl -s -X POST "$SUPPLIER/api/simulate/slow" | jq .
info "Every request to the supplier now takes 30 seconds."
info "But our RestTemplate has a 3s timeout — calls will fail fast."
pause

# --------------------------------------------------------------------------
banner "PHASE 3: Circuit Breaker in Action"

step "Sending requests — watch the circuit breaker react..."
echo ""

for i in $(seq 1 6); do
  STATE=$(cb_state)
  echo -e "  ${CYAN}[CB state: $STATE]${NC}"
  timed_request "Request $i"

  if [ "$i" -eq 3 ]; then
    echo ""
    info ">>> 3 failures recorded (>50% failure rate) — Circuit should OPEN!"
    echo ""
  fi
done

echo ""
show_cb
pause

# --------------------------------------------------------------------------
banner "PHASE 4: Service is Still ALIVE!"

step "Sending 10 concurrent requests to the store..."
info "Even under load, the circuit breaker returns fallback INSTANTLY."
echo ""

for i in $(seq 1 10); do
  (
    START=$(date +%s%N)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$STORE/store/products")
    END=$(date +%s%N)
    ELAPSED=$(( (END - START) / 1000000 ))
    echo -e "  ${GREEN}Request $i: HTTP $HTTP_CODE in ${ELAPSED}ms${NC}"
  ) &
done
wait
echo ""

step "Health check — does the store still respond?"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$STORE/store/health")
if [ "$HTTP_CODE" = "200" ]; then
  info "/store/health → HTTP 200 — SERVICE IS ALIVE AND RESPONSIVE!"
  curl -s "$STORE/store/health" | jq .
else
  fail "/store/health → HTTP $HTTP_CODE"
fi

echo ""
info "Compare this with the NO circuit breaker demo:"
info "  Without CB: /health TIMED OUT — service was DEAD"
info "  With CB:    /health responds instantly — service SURVIVES"
pause

# --------------------------------------------------------------------------
banner "PHASE 5: Recovery — Supplier Comes Back"

step "Restoring supplier to HEALTHY mode..."
curl -s -X POST "$SUPPLIER/api/simulate/healthy" | jq .
echo ""

step "Circuit is OPEN. Waiting for it to transition to HALF_OPEN (15s)..."
for i in $(seq 15 -1 1); do
  printf "\r  Countdown: %2ds " "$i"
  sleep 1
done
echo ""
echo ""

show_cb
STATE=$(cb_state)
echo ""
if [ "$STATE" = "HALF_OPEN" ]; then
  info "Circuit is HALF_OPEN — allowing test requests through."
else
  info "Circuit state: $STATE (may need a request to trigger transition)"
  # Trigger the transition
  curl -s "$STORE/store/products" > /dev/null 2>&1
  sleep 1
  show_cb
fi
pause

# --------------------------------------------------------------------------
banner "PHASE 6: Test Requests in HALF_OPEN State"

step "Sending test requests — if they succeed, circuit will CLOSE..."
echo ""

timed_request "Test request 1"
timed_request "Test request 2"

echo ""
show_cb
STATE=$(cb_state)

if [ "$STATE" = "CLOSED" ]; then
  echo ""
  info "Circuit is back to CLOSED! Full recovery complete."
  echo ""
  step "Verifying normal operation..."
  for i in 1 2 3; do
    timed_request "Request $i"
  done
else
  info "Circuit state: $STATE"
fi
pause

# --------------------------------------------------------------------------
banner "SUMMARY: Circuit Breaker State Transitions"

echo -e "  ${GREEN}CLOSED${NC}  ──(failures exceed threshold)──▶  ${RED}OPEN${NC}"
echo -e "     ▲                                          │"
echo -e "     │                                     (wait 15s)"
echo -e "     │                                          │"
echo -e "     │                                          ▼"
echo -e "     └──────(test requests succeed)────  ${YELLOW}HALF_OPEN${NC}"
echo ""
echo -e "  Phase 1: ${GREEN}CLOSED${NC}    — Normal operation, requests flow through"
echo -e "  Phase 3: ${RED}OPEN${NC}      — Failures detected, fallback returned instantly"
echo -e "  Phase 5: ${YELLOW}HALF_OPEN${NC} — Timeout expired, test requests allowed"
echo -e "  Phase 6: ${GREEN}CLOSED${NC}    — Tests passed, back to normal"
echo ""
echo -e "  ${GREEN}Key benefit:${NC} The store service remained responsive throughout."
echo -e "  Threads were never exhausted. /health always responded."
echo ""
