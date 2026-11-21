import java.util.*;
import it.unibo.ai.didattica.competition.tablut.domain.*;
import datalinkk.Engine;

public class RulesCheck {
    static int[] toBoard(State s) {
        int[] b = new int[81];
        State.Pawn[][] bd = s.getBoard();
        for (int r = 0; r < 9; r++) for (int c = 0; c < 9; c++) {
            State.Pawn p = bd[r][c];
            b[r*9+c] = p == State.Pawn.WHITE ? 1 : p == State.Pawn.BLACK ? 2 : p == State.Pawn.KING ? 3 : 0;
        }
        return b;
    }
    static String sq(int idx) { return "" + (char) ('a' + idx % 9) + (idx / 9 + 1); }

    public static void main(String[] a) throws Exception {
        int games = Integer.parseInt(a[0]);
        Random rnd = new Random(7);
        long checked = 0, mismatches = 0, positions = 0, whiteWins = 0, blackWins = 0, caps = 0;
        java.io.PrintStream realOut = System.out;
        for (int g = 0; g < games; g++) {
            State st = new StateTablut();
            st.setTurn(State.Turn.WHITE);
            GameAshtonTablut game = new GameAshtonTablut(999, 0, "garbage", "fake", "fake");
            for (int ply = 0; ply < 250; ply++) {
                int side = st.getTurn() == State.Turn.WHITE ? 0 : 1;
                int[] b = toBoard(st);
                int[] mv = new int[700];
                int n = Engine.genMoves(b, side, mv);
                Set<Integer> mine = new HashSet<>();
                for (int i = 0; i < n; i++) mine.add(mv[i]);
                positions++;
                // all candidate moves along lines
                for (int from = 0; from < 81; from++) {
                    int p = b[from];
                    if (p == 0) continue;
                    // test pieces of both colors: engine rejects wrong-color moves; only test own pieces
                    if (side == 0 ? (p != 1 && p != 3) : p != 2) continue;
                    for (int to = 0; to < 81; to++) {
                        int fr = from/9, fc = from%9, tr = to/9, tc = to%9;
                        if (!(fr == tr || fc == tc)) continue;
                        if (from == to) continue;
                        boolean ok;
                        State cl = st.clone();
                        Action act = new Action(sq(from), sq(to), st.getTurn());
                        State res = null;
                        try {
                            System.setOut(new java.io.PrintStream(new java.io.OutputStream(){public void write(int x){}}));
                            res = game.checkMove(cl, act);
                            ok = true;
                        } catch (Exception e) { ok = false; }
                        finally { System.setOut(realOut); }
                        checked++;
                        boolean m = mine.contains(from * 81 + to);
                        if (ok != m) { mismatches++; if (mismatches < 10) realOut.println("LEGALITY mismatch game "+g+" ply "+ply+" "+sq(from)+"-"+sq(to)+" engine="+ok+" mine="+m); }
                        if (ok && m) {
                            int[] nb = b.clone();
                            int s2 = Engine.apply(nb, from, to, side);
                            int[] eb = toBoard(res);
                            boolean same = Arrays.equals(nb, eb);
                            State.Turn t = res.getTurn();
                            int es = t == State.Turn.WHITEWIN ? 1 : t == State.Turn.BLACKWIN ? 2 : 0;
                            if (!same || es != s2) { mismatches++; if (mismatches < 10) realOut.println("RESULT mismatch game "+g+" ply "+ply+" "+sq(from)+"-"+sq(to)+" boards same="+same+" status engine="+es+" mine="+s2); }
                        }
                    }
                }
                if (n == 0) break;
                int pick = mv[rnd.nextInt(n)];
                // prefer captures sometimes to exercise capture logic: choose among moves that change piece count
                if (rnd.nextInt(3) == 0) {
                    List<Integer> cap = new ArrayList<>();
                    for (int i = 0; i < n; i++) { int[] nb = b.clone(); Engine.apply(nb, mv[i]/81, mv[i]%81, side); if (Engine.countPieces(nb) < Engine.countPieces(b) || nb[0]==99) cap.add(mv[i]); }
                    if (!cap.isEmpty()) pick = cap.get(rnd.nextInt(cap.size()));
                }
                Action act = new Action(sq(pick/81), sq(pick%81), st.getTurn());
                System.setOut(new java.io.PrintStream(new java.io.OutputStream(){public void write(int x){}}));
                State res = game.checkMove(st.clone(), act);
                System.setOut(realOut);
                st = res;
                if (st.getTurn() == State.Turn.WHITEWIN) { whiteWins++; break; }
                if (st.getTurn() == State.Turn.BLACKWIN) { blackWins++; break; }
                if (st.getTurn() == State.Turn.DRAW) break;
            }
        }
        realOut.println("positions=" + positions + " movesChecked=" + checked + " mismatches=" + mismatches + " whiteWins=" + whiteWins + " blackWins=" + blackWins);
    }
}
