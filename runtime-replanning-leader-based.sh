#!/usr/bin/env sh
DESTINATION="$HOME/Downloads/runtime-replanning-leader-based-$(date --utc "+%F-%H.%M.%S")"
git clone https://github.com/angelacorte/experiments-2025-acsos-robots "$DESTINATION"
cd "$DESTINATION"
MAX_SEED=0 LEADER_BASED=true ./gradlew runDepotsRuntimeGraphic