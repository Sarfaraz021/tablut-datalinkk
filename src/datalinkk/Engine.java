package datalinkk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

/**
 * Fast Ashton-Tablut engine (rules ported from the course server GameAshtonTablut)
 * plus iterative-deepening alpha-beta search with a transposition table.
 *
 * Board: int[81], index = row*9+col, row 0 is "1", col 0 is "a" (like the server).
 * 0 empty, 1 white soldier, 2 black soldier, 3 king. The throne (index 40) is stored
 * as 0 when it is empty but it can never be entered or crossed.
 */
public final class Engine {
    public static final int EMPTY = 0, W = 1, B = 2, K = 3;
    public static final int THRONE = 40;
    public static final int WHITE_SIDE = 0, BLACK_SIDE = 1;
    public static final int ST_NORMAL = 0, ST_WHITEWIN = 1, ST_BLACKWIN = 2;

    static final boolean[] CIT = new boolean[81];
    static final boolean[] CIT_SPECIAL = new boolean[81]; // a5, i5, e1, e9: not an anvil for white captures
    static final boolean[] GATE = new boolean[81];
    static final long[][] Z = new long[81][4];
    static final long Z_BLACK;
    static {
        int[][] cits = {{3,0},{4,0},{5,0},{4,1},{0,3},{0,4},{0,5},{1,4},{3,8},{4,8},{5,8},{4,7},{8,3},{8,4},{8,5},{7,4}};
        for (int[] x : cits) CIT[x[0] * 9 + x[1]] = true;
        CIT_SPECIAL[4 * 9 + 0] = CIT_SPECIAL[4 * 9 + 8] = CIT_SPECIAL[0 * 9 + 4] = CIT_SPECIAL[8 * 9 + 4] = true;
        int[][] gates = {{1,2},{2,1},{1,6},{2,7},{6,1},{7,2},{6,7},{7,6}};
        for (int[] x : gates) GATE[x[0] * 9 + x[1]] = true;
        Random rnd = new Random(20261121L);
        for (int i = 0; i < 81; i++) for (int j = 0; j < 4; j++) Z[i][j] = rnd.nextLong();
        Z_BLACK = rnd.nextLong();
    }
    static final int[] DR = {-1, 1, 0, 0};
    static final int[] DC = {0, 0, -1, 1};

    // ------------------------------------------------------------------ rules

    /** Generates moves (from*81+to) for side into out; returns count. */
    public static int genMoves(int[] b, int side, int[] out) {
        int n = 0;
        for (int sq = 0; sq < 81; sq++) {
            int p = b[sq];
            if (p == EMPTY) continue;
            if (side == WHITE_SIDE ? (p != W && p != K) : (p != B)) continue;
            int r = sq / 9, c = sq % 9;
            boolean fromCit = CIT[sq];
            for (int d = 0; d < 4; d++) {
                int nr = r + DR[d], nc = c + DC[d], dist = 1;
                while (nr >= 0 && nr < 9 && nc >= 0 && nc < 9) {
                    int t = nr * 9 + nc;
                    if (t == THRONE || b[t] != EMPTY) break;
                    if (CIT[t] && !fromCit) break;
                    if (!(CIT[t] && fromCit && dist > 5)) out[n++] = sq * 81 + t;
                    nr += DR[d]; nc += DC[d]; dist++;
                }
            }
        }
        return n;
    }

    static boolean anvilForWhite(int[] b, int sq) {
        int v = b[sq];
        if (v == W || v == K || sq == THRONE) return true;
        return CIT[sq] && !CIT_SPECIAL[sq];
    }

    static boolean anvilForBlack(int[] b, int sq) {
        return b[sq] == B || sq == THRONE || CIT[sq];
    }

    /** Applies a move in place (board must be a private copy). Returns game status after the move. */
    public static int apply(int[] b, int from, int to, int side) {
        int p = b[from];
        b[from] = EMPTY;
        b[to] = p;
        int r = to / 9, c = to % 9;
        int status = ST_NORMAL;
        if (side == WHITE_SIDE) {
            // captures of black soldiers
            for (int d = 0; d < 4; d++) {
                int r1 = r + DR[d], c1 = c + DC[d], r2 = r + 2 * DR[d], c2 = c + 2 * DC[d];
                if (r2 < 0 || r2 > 8 || c2 < 0 || c2 > 8) continue;
                int s1 = r1 * 9 + c1, s2 = r2 * 9 + c2;
                if (b[s1] == B && anvilForWhite(b, s2)) b[s1] = EMPTY;
            }
            if (p == K && (r == 0 || r == 8 || c == 0 || c == 8)) status = ST_WHITEWIN;
        } else {
            for (int d = 0; d < 4; d++) {
                int r1 = r + DR[d], c1 = c + DC[d], r2 = r + 2 * DR[d], c2 = c + 2 * DC[d];
                if (r2 < 0 || r2 > 8 || c2 < 0 || c2 > 8) continue;
                int s1 = r1 * 9 + c1, s2 = r2 * 9 + c2;
                if (b[s1] == W && anvilForBlack(b, s2)) b[s1] = EMPTY;
            }
            // king capture
            for (int d = 0; d < 4; d++) {
                int r1 = r + DR[d], c1 = c + DC[d], r2 = r + 2 * DR[d], c2 = c + 2 * DC[d];
                if (r2 < 0 || r2 > 8 || c2 < 0 || c2 > 8) continue;
                int s1 = r1 * 9 + c1, s2 = r2 * 9 + c2;
                if (b[s1] != K) continue;
                if (s1 == THRONE) {
                    // all four neighbours black (the mover is one of them)
                    if (b[3 * 9 + 4] == B && b[5 * 9 + 4] == B && b[4 * 9 + 3] == B && b[4 * 9 + 5] == B) status = ST_BLACKWIN;
                } else if (s1 == 3 * 9 + 4 || s1 == 5 * 9 + 4 || s1 == 4 * 9 + 3 || s1 == 4 * 9 + 5) {
                    // adjacent to the throne: the three free sides must be black
                    int cnt = 0, need = 0;
                    int kr = s1 / 9, kc = s1 % 9;
                    for (int e = 0; e < 4; e++) {
                        int q = (kr + DR[e]) * 9 + (kc + DC[e]);
                        if (q == THRONE) continue;
                        need++;
                        if (b[q] == B) cnt++;
                    }
                    if (cnt == need) status = ST_BLACKWIN;
                } else {
                    if (b[s2] == B || CIT[s2]) status = ST_BLACKWIN;
                }
            }
        }
        return status;
    }

    public static long hash(int[] b, int side) {
        long h = side == BLACK_SIDE ? Z_BLACK : 0L;
        for (int i = 0; i < 81; i++) if (b[i] != 0) h ^= Z[i][b[i]];
        return h;
    }

    public static int countPieces(int[] b) {
        int n = 0;
        for (int i = 0; i < 81; i++) if (b[i] != 0) n++;
        return n;
    }

    // ------------------------------------------------------------------ evaluation (white perspective)

    static final int MATE = 100000;

    /** king moves: returns mobility in [0..], escape count in out[0] */
    static int kingInfo(int[] b, int ksq, int[] esc) {
        int mob = 0, e = 0;
        int r = ksq / 9, c = ksq % 9;
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            while (nr >= 0 && nr < 9 && nc >= 0 && nc < 9) {
                int t = nr * 9 + nc;
                if (t == THRONE || b[t] != EMPTY || CIT[t]) break;
                mob++;
                if (nr == 0 || nr == 8 || nc == 0 || nc == 8) e++;
                nr += DR[d]; nc += DC[d];
            }
        }
        esc[0] = e;
        return mob;
    }

    public static int evaluate(int[] b, int sideToMove) {
        int w = 0, bl = 0, ksq = -1, gates = 0;
        for (int i = 0; i < 81; i++) {
            int p = b[i];
            if (p == W) w++;
            else if (p == B) { bl++; if (GATE[i]) gates++; }
            else if (p == K) ksq = i;
        }
        if (ksq < 0) return -MATE / 2;
        int[] esc = new int[1];
        int mob = kingInfo(b, ksq, esc);
        int r = ksq / 9, c = ksq % 9;
        int minDist = Math.min(Math.min(r, c), Math.min(8 - r, 8 - c));
        int adjB = 0;
        for (int d = 0; d < 4; d++) {
            int nr = r + DR[d], nc = c + DC[d];
            if (nr < 0 || nr > 8 || nc < 0 || nc > 8) continue;
            int q = nr * 9 + nc;
            if (b[q] == B) adjB++;
            else if (CIT[q]) adjB++;
        }
        int s = 0;
        s += 100 * w - 55 * bl;
        s += 6 * mob;
        s += 18 * (4 - minDist);
        s -= 35 * adjB;
        s -= 22 * gates;
        if (sideToMove == BLACK_SIDE) {
            if (esc[0] >= 2) s += 6000;
            else if (esc[0] == 1) s += 350;
        } else {
            if (esc[0] >= 1) s += MATE / 2;
        }
        return s;
    }

    // ------------------------------------------------------------------ search

    private static final int TT_BITS = 21;
    private static final int TT_SIZE = 1 << TT_BITS;
    private final long[] ttKey = new long[TT_SIZE];
    private final int[] ttVal = new int[TT_SIZE];
    private final int[] ttMove = new int[TT_SIZE];
    private final byte[] ttDepth = new byte[TT_SIZE];
    private final byte[] ttFlag = new byte[TT_SIZE]; // 0 none, 1 exact, 2 lower, 3 upper
    private final int[][] hist = new int[2][81 * 81];
    private final int[][] killers = new int[64][2];

    private long deadline;
    private boolean aborted;
    public long nodes;
    public int lastDepth;
    public int lastScore;
    private final HashSet<Long> history = new HashSet<Long>();
    private final long[] path = new long[128];

    public void clearHistory() { history.clear(); }
    public void addHistory(long h) { history.add(h); }

    private int[][] moveBuf = new int[64][700];

    /** Returns best move (from*81+to) for side, searching until the deadline (System.nanoTime based). */
    public int search(int[] board, int side, long deadlineNanos, int maxDepth) {
        this.deadline = deadlineNanos;
        this.aborted = false;
        this.nodes = 0;
        for (int i = 0; i < 2; i++) Arrays.fill(hist[i], 0);
        int[] rootMoves = new int[700];
        int n = genMoves(board, side, rootMoves);
        if (n == 0) return -1;
        int best = rootMoves[0];
        long rootHash = hash(board, side);
        // immediate win shortcut
        for (int i = 0; i < n; i++) {
            int[] nb = board.clone();
            int st = apply(nb, rootMoves[i] / 81, rootMoves[i] % 81, side);
            if ((side == WHITE_SIDE && st == ST_WHITEWIN) || (side == BLACK_SIDE && st == ST_BLACKWIN)) return rootMoves[i];
        }
        int bestScore = 0;
        for (int depth = 1; depth <= maxDepth; depth++) {
            path[0] = rootHash;
            int[] res = rootSearch(board, side, depth, rootMoves, n, best);
            if (aborted) break;
            best = res[0];
            bestScore = res[1];
            lastDepth = depth;
            lastScore = bestScore;
            if (Math.abs(bestScore) > MATE - 200) break; // forced result found
        }
        return best;
    }

    private int[] rootSearch(int[] board, int side, int depth, int[] moves, int n, int prevBest) {
        // order: previous best first
        for (int i = 0; i < n; i++) if (moves[i] == prevBest) { int t = moves[0]; moves[0] = moves[i]; moves[i] = t; break; }
        int alpha = -MATE * 2, beta = MATE * 2;
        int bestMove = moves[0], bestVal = -MATE * 2;
        int[] scores = new int[n];
        for (int i = 0; i < n; i++) {
            int mv = moves[i];
            int[] nb = board.clone();
            int st = apply(nb, mv / 81, mv % 81, side);
            int val;
            if (st != ST_NORMAL) {
                val = MATE - 1;
            } else {
                long h = hash(nb, 1 - side);
                if (history.contains(h)) val = 0;
                else {
                    path[1] = h;
                    val = -negamax(nb, 1 - side, depth - 1, -beta, -alpha, 1);
                }
            }
            if (aborted) return new int[]{bestMove, bestVal};
            scores[i] = val;
            if (val > bestVal) { bestVal = val; bestMove = mv; }
            if (val > alpha) alpha = val;
        }
        // keep move list sorted by score for next iteration
        for (int i = 0; i < n - 1; i++) for (int j = i + 1; j < n; j++) if (scores[j] > scores[i]) {
            int t = scores[i]; scores[i] = scores[j]; scores[j] = t;
            t = moves[i]; moves[i] = moves[j]; moves[j] = t;
        }
        return new int[]{bestMove, bestVal};
    }

    private int negamax(int[] b, int side, int depth, int alpha, int beta, int ply) {
        nodes++;
        if ((nodes & 2047) == 0 && System.nanoTime() > deadline) aborted = true;
        if (aborted) return 0;
        if (depth <= 0) {
            int white = evaluate(b, side);
            return side == WHITE_SIDE ? white : -white;
        }

        long h = path[ply];
        int idx = (int) (h ^ (h >>> 32)) & (TT_SIZE - 1);
        int ttm = 0;
        int alphaOrig = alpha;
        if (ttKey[idx] == h && ttFlag[idx] != 0) {
            ttm = ttMove[idx];
            if (ttDepth[idx] >= depth) {
                int v = ttVal[idx];
                int f = ttFlag[idx];
                if (f == 1) return v;
                if (f == 2 && v > alpha) alpha = v;
                else if (f == 3 && v < beta) beta = v;
                if (alpha >= beta) return v;
            }
        }
        int[] mv = moveBuf[ply];
        int n = genMoves(b, side, mv);
        if (n == 0) return -(MATE - ply);
        // ordering scores
        int[] sc = new int[n];
        int[] hs = hist[side];
        for (int i = 0; i < n; i++) {
            int m = mv[i];
            int s = hs[m];
            if (m == ttm) s += 1000000;
            if (ply < 64) { if (m == killers[ply][0]) s += 20000; else if (m == killers[ply][1]) s += 10000; }
            int p = b[m / 81];
            if (p == K) {
                int to = m % 81, rr = to / 9, cc = to % 9;
                if (rr == 0 || rr == 8 || cc == 0 || cc == 8) s += 900000;
                else s += 500;
            }
            sc[i] = s;
        }
        int bestVal = -MATE * 2, bestMove = 0;
        for (int k = 0; k < n; k++) {
            int bi = k;
            for (int j = k + 1; j < n; j++) if (sc[j] > sc[bi]) bi = j;
            if (bi != k) { int t = sc[k]; sc[k] = sc[bi]; sc[bi] = t; t = mv[k]; mv[k] = mv[bi]; mv[bi] = t; }
            int m = mv[k];
            int[] nb = b.clone();
            int st = apply(nb, m / 81, m % 81, side);
            int val;
            if (st != ST_NORMAL) {
                val = MATE - ply;
            } else {
                long nh = hash(nb, 1 - side);
                boolean rep = history.contains(nh);
                if (!rep) for (int q = ply; q >= 0; q -= 1) if (path[q] == nh) { rep = true; break; }
                if (rep) val = 0;
                else {
                    path[ply + 1] = nh;
                    val = -negamax(nb, 1 - side, depth - 1, -beta, -alpha, ply + 1);
                }
            }
            if (aborted) return 0;
            if (val > bestVal) { bestVal = val; bestMove = m; }
            if (val > alpha) alpha = val;
            if (alpha >= beta) {
                hs[m] += depth * depth;
                if (ply < 64 && killers[ply][0] != m) { killers[ply][1] = killers[ply][0]; killers[ply][0] = m; }
                break;
            }
        }
        ttKey[idx] = h; ttVal[idx] = bestVal; ttMove[idx] = bestMove; ttDepth[idx] = (byte) depth;
        ttFlag[idx] = (byte) (bestVal <= alphaOrig ? 3 : (bestVal >= beta ? 2 : 1));
        return bestVal;
    }
}
