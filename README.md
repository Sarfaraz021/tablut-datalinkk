# DataLinkk Tablut player

Player for the Tablut Challenge (Fundamentals of AI and KR, Unibo 2026/27).

- Language: Java (compiled for Java 8). Uses the course server classes and gson.
- Method: alpha-beta search (negamax) with iterative deepening, a transposition table,
  move ordering (previous best move, killer and history moves, king-to-edge first) and a time limit.
- Own fast rules engine written from the server's GameAshtonTablut. It was checked against the
  server engine on about 350,000 random moves (moves, captures, king capture, wins): 0 differences
  (see test/RulesCheck.java).
- Evaluation: material, king mobility and distance to the edge, black pieces around the king,
  black pieces on the 8 "gate" squares, and king escape threats (two free escape lines = win).
- Draw by repeated state: states are remembered and repeats are scored as draws.

## Run

    ./runmyplayer.sh WHITE 60 192.168.20.101

Parameters, in this order: role (WHITE or BLACK, case-insensitive), timeout in seconds, server IP.
No other parameters are needed. The player stops searching about 1/8 of the timeout (1.5 to 8 s)
before the limit, so it never goes over time.

## Rebuild (only if you change the code)

    javac --release 8 -encoding iso-8859-1 -cp lib/gson-2.2.2.jar:lib/commons-cli-1.4.jar \
          -d build <course server sources> src/datalinkk/*.java
    jar cf player.jar -C build .
