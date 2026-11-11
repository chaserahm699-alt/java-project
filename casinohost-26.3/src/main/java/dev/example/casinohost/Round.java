package dev.example.casinohost;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** One automatic game between the host and a viewer (a player who paid). */
public class Round {
    public enum Type { ROULETTE, BLACKJACK }
    public enum Outcome { VIEWER, HOST, PUSH }

    static final SecureRandom RNG = new SecureRandom();
    static final Set<Integer> REDS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    public final Type type;
    public final String viewer;
    public final long bet;
    public Outcome outcome;
    public boolean announced;
    public boolean viewerNatural;
    int ticks, endTicks;

    // Roulette (European, single zero). Viewer's colour is picked at random.
    public final boolean viewerRed = RNG.nextBoolean();
    public int shownNum = 0;
    final int finalNum = RNG.nextInt(37);

    // Blackjack (both sides auto-play: hit below 17, stand on 17+)
    public final List<Integer> deck = new ArrayList<>();
    public final List<Integer> viewerHand = new ArrayList<>();
    public final List<Integer> hostHand = new ArrayList<>();
    public int phase = 0;

    public Round(Type type, String viewer, long bet) {
        this.type = type;
        this.viewer = viewer;
        this.bet = bet;
        for (int i = 0; i < 4; i++) for (int r = 1; r <= 13; r++) deck.add(r);
        Collections.shuffle(deck, RNG);
    }

    public void tick() {
        if (outcome != null) { endTicks++; return; }
        ticks++;
        if (type == Type.ROULETTE) {
            if (ticks % 3 == 0 && ticks < 70) shownNum = RNG.nextInt(37);
            if (ticks >= 70) {
                shownNum = finalNum;
                boolean win = finalNum != 0 && REDS.contains(finalNum) == viewerRed;
                outcome = win ? Outcome.VIEWER : Outcome.HOST;
            }
        } else {
            blackjackStep();
        }
    }

    private void blackjackStep() {
        if (ticks % 20 != 0) return; // one action per second so it's watchable
        switch (phase) {
            case 0 -> {
                viewerHand.add(draw()); hostHand.add(draw());
                viewerHand.add(draw()); hostHand.add(draw());
                boolean vn = total(viewerHand) == 21, hn = total(hostHand) == 21;
                if (vn || hn) {
                    outcome = (vn && hn) ? Outcome.PUSH : vn ? Outcome.VIEWER : Outcome.HOST;
                    viewerNatural = vn && !hn;
                    phase = 3;
                } else phase = 1;
            }
            case 1 -> {
                int t = total(viewerHand);
                if (t > 21) outcome = Outcome.HOST;
                else if (t < 17) viewerHand.add(draw());
                else phase = 2;
            }
            case 2 -> {
                int h = total(hostHand), v = total(viewerHand);
                if (h > 21) outcome = Outcome.VIEWER;
                else if (h < 17) hostHand.add(draw());
                else outcome = v > h ? Outcome.VIEWER : v == h ? Outcome.PUSH : Outcome.HOST;
            }
            default -> { }
        }
    }

    private int draw() { return deck.remove(deck.size() - 1); }

    public boolean hostCardHidden() { return type == Type.BLACKJACK && outcome == null && phase < 2; }
    public boolean done() { return outcome != null && endTicks >= 120; }

    /** Amount owed back to the viewer (stake included). 0 if the host won. */
    public long payout() {
        if (outcome == Outcome.VIEWER) return (type == Type.BLACKJACK && viewerNatural) ? bet * 5 / 2 : bet * 2;
        if (outcome == Outcome.PUSH) return bet;
        return 0;
    }

    public int rouletteNumber() { return shownNum; }
    public static boolean isRed(int n) { return REDS.contains(n); }

    public static int total(List<Integer> h) {
        int sum = 0, aces = 0;
        for (int c : h) { if (c == 1) { aces++; sum += 11; } else sum += Math.min(c, 10); }
        while (sum > 21 && aces-- > 0) sum -= 10;
        return sum;
    }

    public static String card(int c) {
        return switch (c) { case 1 -> "A"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; default -> String.valueOf(c); };
    }
}
