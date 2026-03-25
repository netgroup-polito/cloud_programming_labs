#!/bin/bash
# ============================================================================
#  DEMO: WITHOUT Circuit Breaker — Cascading Failure
# ============================================================================

SUPPLIER="http://localhost:8080"
STORE="http://localhost:8081"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

banner() { echo -e "\n${CYAN}════════════════════════════════════════════════════════════════${NC}"; echo -e "${BOLD}  $1${NC}"; echo -e "${CYAN}════════════════════════════════════════════════════════════════${NC}\n"; }
step()   { echo -e "${YELLOW}▶ $1${NC}"; }
info()   { echo -e "${GREEN}  $1${NC}"; }
fail()   { echo -e "${RED}  ✗ $1${NC}"; }
pause()  { echo ""; read -rp "  [Press ENTER to continue] "; echo ""; }

# --------------------------------------------------------------------------
banner "DEMO: Store WITHOUT Circuit Breaker"
echo -e "This demo shows how a slow downstream service causes ${RED}CASCADING FAILURE${NC}"
echo -e "when the caller has ${RED}NO circuit breaker${NC} protection."
echo ""
echo -e "  Store Service (no CB):  ${BOLD}$STORE${NC}   (Tomcat maxThreads = 5)"
echo -e "  Supplier Service:       ${BOLD}$SUPPLIER${NC}"
pause

# --------------------------------------------------------------------------
banner "PHASE 1: Normal Operation"

step "Resetting supplier to HEALTHY mode..."
curl -s -X POST "$SUPPLIER/api/simulate/healthy" | jq .
echo ""

step "Calling store → supplier (should work fine)..."
for i in 1 2 3; do
  echo -e "  Request $i:"
  START=$(date +%s%N)
  RESPONSE=$(curl -s "$STORE/store/products")
  END=$(date +%s%N)
  ELAPSED=$(( (END - START) / 1000000 ))
  echo "$RESPONSE" | jq -r '.[0:2] | .[] | "    \(.name) - $\(.price)"' 2>/dev/null || echo "    $RESPONSE"
  info "Completed in ${ELAPSED}ms"
done
echo ""

step "Health check..."
curl -s "$STORE/store/health" | jq .

info "Everything works perfectly. Responses are fast."
pause

# --------------------------------------------------------------------------
banner "PHASE 2: Supplier Becomes SLOW (30s delay)"

step "Switching supplier to SLOW mode..."
curl -s -X POST "$SUPPLIER/api/simulate/slow" | jq .
info "Every request to the supplier now takes 30 seconds to respond."
pause

# --------------------------------------------------------------------------
banner "PHASE 3: Cascading Failure"

step "Sending 6 concurrent requests to the store service..."
info "The store has only 5 Tomcat threads. Watch what happens."
echo ""

for i in $(seq 1 6); do
  (
    START=$(date +%s%N)
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 35 "$STORE/store/products")
    END=$(date +%s%N)
    ELAPSED=$(( (END - START) / 1000000 ))
    if [ "$HTTP_CODE" = "200" ]; then
      echo -e "  ${GREEN}Request $i: HTTP $HTTP_CODE — took ${ELAPSED}ms (~30s hanging on supplier)${NC}"
    else
      echo -e "  ${RED}Request $i: HTTP $HTTP_CODE — took ${ELAPSED}ms${NC}"
    fi
  ) &
done

sleep 2
echo ""
fail "5 threads are now BLOCKED waiting for the slow supplier..."
fail "Thread pool is EXHAUSTED. The store service is effectively DEAD."
echo ""

step "Trying /store/health while threads are stuck (5s timeout)..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "$STORE/store/health")
if [ "$HTTP_CODE" = "000" ]; then
  fail "/store/health TIMED OUT — the service is COMPLETELY UNRESPONSIVE!"
  fail "Even a simple health check cannot be served."
  fail ""
  fail "THIS IS THE CASCADING FAILURE:"
  fail "The supplier's slowness has propagated to the store service."
  fail "The store cannot handle ANY request — not even ones that"
  fail "don't need the supplier."
else
  echo -e "  HTTP $HTTP_CODE (service responded, threads may have freed up)"
fi

echo ""
step "Waiting for the 6 background requests to complete..."
wait
echo ""
info "All threads eventually freed after ~30s."

step "Health check after recovery:"
curl -s "$STORE/store/health" | jq .
pause

# --------------------------------------------------------------------------
banner "PHASE 4: Summary"

echo -e "  ${RED}WITHOUT Circuit Breaker:${NC}"
echo ""
echo -e "  • Supplier became slow (30s delay)"
echo -e "  • Store threads hung for 30s each waiting for supplier"
echo -e "  • With only 5 threads, the pool was exhausted after 5 requests"
echo -e "  • The store became ${RED}COMPLETELY UNRESPONSIVE${NC}"
echo -e "  • Even /store/health could not be served"
echo -e "  • ${RED}CASCADING FAILURE${NC}: one slow service brought down the caller"
echo ""
echo -e "  Now run ${BOLD}./demo-cb.sh${NC} to see how the Circuit Breaker prevents this."
echo ""

# Reset supplier
curl -s -X POST "$SUPPLIER/api/simulate/healthy" > /dev/null 2>&1
