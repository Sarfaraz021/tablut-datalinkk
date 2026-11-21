package datalinkk;

import java.io.IOException;

import it.unibo.ai.didattica.competition.tablut.client.TablutClient;
import it.unibo.ai.didattica.competition.tablut.domain.Action;
import it.unibo.ai.didattica.competition.tablut.domain.State;
import it.unibo.ai.didattica.competition.tablut.domain.State.Pawn;
import it.unibo.ai.didattica.competition.tablut.domain.State.Turn;

/**
 * Tablut player "DataLinkk": alpha-beta search with iterative deepening and a time limit.
 * Usage: DataLinkkPlayer ROLE [TIMEOUT_SECONDS] [SERVER_IP]   (ROLE is case-insensitive)
 */
public class DataLinkkPlayer extends TablutClient {

    private final Engine engine = new Engine();
    private final int timeoutSec;
    private int lastPieces = -1;
    private final boolean verbose;

    public DataLinkkPlayer(String role, String name, int timeout, String ip, boolean verbose) throws IOException {
        super(role, name, timeout, ip);
        this.timeoutSec = timeout;
        this.verbose = verbose;
    }

    public static void main(String[] args) throws Exception {
        String role = "WHITE";
        int timeout = 60;
        String ip = "localhost";
        if (args.length < 1) {
            System.err.println("Usage: DataLinkkPlayer WHITE|BLACK [timeout_seconds] [server_ip]");
            System.exit(1);
        }
        role = args[0].trim().toUpperCase();
        if (!role.equals("WHITE") && !role.equals("BLACK")) {
            System.err.println("Role must be WHITE or BLACK");
            System.exit(1);
        }
        if (args.length >= 2) {
            try { timeout = Integer.parseInt(args[1].trim()); } catch (NumberFormatException e) { timeout = 60; }
        }
        if (args.length >= 3) ip = args[2].trim();
        boolean verbose = System.getProperty("tablut.verbose") != null;
        DataLinkkPlayer p = new DataLinkkPlayer(role, "DataLinkk", timeout, ip, verbose);
        p.run();
    }

    static int[] toBoard(State s) {
        int[] b = new int[81];
        Pawn[][] bd = s.getBoard();
        for (int r = 0; r < 9; r++)
            for (int c = 0; c < 9; c++) {
                Pawn p = bd[r][c];
                int v = 0;
                if (p == Pawn.WHITE) v = Engine.W;
                else if (p == Pawn.BLACK) v = Engine.B;
                else if (p == Pawn.KING) v = Engine.K;
                b[r * 9 + c] = v;
            }
        return b;
    }

    static String sq(int idx) {
        return "" + (char) ('a' + idx % 9) + (idx / 9 + 1);
    }

    @Override
    public void run() {
        try {
            this.declareName();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        final boolean white = this.getPlayer() == Turn.WHITE;
        while (true) {
            try {
                this.read();
            } catch (Exception e) {
                System.exit(1);
            }
            long t0 = System.nanoTime();
            State st = this.getCurrentState();
            Turn turn = st.getTurn();
            if (turn == Turn.WHITEWIN || turn == Turn.BLACKWIN || turn == Turn.DRAW) {
                String res = turn == Turn.DRAW ? "DRAW" :
                        ((turn == Turn.WHITEWIN) == white ? "WIN" : "LOSE");
                System.out.println("Game over: " + res);
                System.exit(0);
            }
            int[] board = toBoard(st);
            int pieces = Engine.countPieces(board);
            if (lastPieces != -1 && pieces != lastPieces) engine.clearHistory();
            lastPieces = pieces;
            int side = turn == Turn.WHITE ? Engine.WHITE_SIDE : Engine.BLACK_SIDE;
            engine.addHistory(Engine.hash(board, side));
            boolean myTurn = (turn == Turn.WHITE && white) || (turn == Turn.BLACK && !white);
            if (!myTurn) continue;

            // time budget: leave a safe margin for JVM pauses and network
            long totalMs = timeoutSec * 1000L;
            long margin = Math.max(1500L, Math.min(8000L, totalMs / 8));
            long budgetMs = Math.max(300L, totalMs - margin);
            long deadline = t0 + budgetMs * 1000000L;
            int move;
            try {
                move = engine.search(board, side, deadline, 40);
            } catch (Throwable ex) {
                ex.printStackTrace();
                move = -1;
            }
            if (move < 0) {
                int[] buf = new int[700];
                int n = Engine.genMoves(board, side, buf);
                move = n > 0 ? buf[0] : -1;
            }
            if (move < 0) {
                // no legal move: nothing sensible to send; send a dummy so the server ends the game
                move = 0;
            }
            try {
                Action a = new Action(sq(move / 81), sq(move % 81), white ? Turn.WHITE : Turn.BLACK);
                if (verbose) System.out.println("depth=" + engine.lastDepth + " score=" + engine.lastScore + " nodes=" + engine.nodes
                        + " ms=" + (System.nanoTime() - t0) / 1000000 + " move=" + a);
                this.write(a);
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
