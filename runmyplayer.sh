#!/bin/bash
# Usage: runmyplayer.sh ROLE TIMEOUT_SECONDS SERVER_IP    (ROLE: WHITE or BLACK, any case)
DIR="$(cd "$(dirname "$0")" && pwd)"
exec java -Xms256m -Xmx2g -cp "$DIR/player.jar:$DIR/lib/gson-2.2.2.jar:$DIR/lib/commons-cli-1.4.jar" datalinkk.DataLinkkPlayer "$@"
