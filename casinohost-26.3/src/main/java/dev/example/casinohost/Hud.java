package dev.example.casinohost;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Hud {

    /** 2D confetti drawn on the HUD (the "fireworks" overlay). */
    public static class Fx {
        public static boolean confettiPending;
        static final List<float[]> parts = new ArrayList<>(); // x, y, vx, vy, colorIndex
        static final Random R = new Random();
    }

    static void drawHead(GuiGraphicsExtractor g, String name, int x, int y, int size) {
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(name);
        var skin = info != null ? info.getSkin()
                : DefaultPlayerSkin.get(UUIDUtil.createOfflinePlayerUUID(name));
        PlayerFaceExtractor.extractRenderState(g, skin, x, y, size);
    }

    /** 26.x GUI colors are ARGB, so force full alpha on plain RGB values. */
    static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int rgb) {
        g.text(f, s, x, y, 0xFF000000 | rgb, true);
    }

    public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
           if (mc.player == null) return;
        Font f = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();

        drawPayers(g, f, sw);
        if (CasinoHostClient.round != null) drawRound(g, f, sw, CasinoHostClient.round);
        drawConfetti(g, sw, sh, delta.getGameTimeDeltaTicks());
    }

    // ---------- payers list (lowest -> highest) ----------
    static void drawPayers(GuiGraphicsExtractor g, Font f, int sw) {
        var list = CasinoHostClient.listed();
        int w = 170, x = sw - w - 6, y = 6, rowH = 18, max = 8;
        int rows = Math.min(list.size(), max);
        int h = 16 + Math.max(rows, 1) * rowH + (list.size() > max ? 12 : 4);
        g.fill(x, y, x + w, y + h, 0xAA000000);
        text(g, f, "Paid 1M+  (low > high)", x + 4, y + 4, 0xFFD700);
        if (list.isEmpty()) text(g, f, "Nobody yet...", x + 4, y + 20, 0x888888);
        for (int i = 0; i < rows; i++) {
            var e = list.get(i);
            int ry = y + 16 + i * rowH;
            drawHead(g, e.getKey(), x + 4, ry + 1, 14);
            text(g, f, e.getKey(), x + 22, ry + 5, i == 0 ? 0x55FF55 : 0xFFFFFF); // green = plays next
            String amt = "$" + CasinoHostClient.fmt(e.getValue());
            text(g, f, amt, x + w - 4 - f.width(amt), ry + 5, 0xFFAA00);
        }
        if (list.size() > max) text(g, f, "+" + (list.size() - max) + " more", x + 4, y + h - 11, 0xAAAAAA);
    }

    // ---------- current game panel + winner banner ----------
    static void drawRound(GuiGraphicsExtractor g, Font f, int sw, Round r) {
        int w = 270, x = sw / 2 - w / 2, y = 6, h = 74;
        g.fill(x, y, x + w, y + h, 0xAA000000);
        drawHead(g, r.viewer, x + 4, y + 4, 24);
        text(g, f, (r.type == Round.Type.ROULETTE ? "ROULETTE" : "BLACKJACK") + "  |  " + r.viewer
                + "  bet $" + CasinoHostClient.fmt(r.bet), x + 34, y + 6, 0xFFFFFF);
        text(g, f, "Viewer vs Host", x + 34, y + 18, 0xAAAAAA);

        if (r.type == Round.Type.ROULETTE) {
            text(g, f, "Viewer bets: " + (r.viewerRed ? "RED" : "BLACK"), x + 8, y + 40,
                    r.viewerRed ? 0xFF5555 : 0xBBBBBB);
            int n = r.rouletteNumber();
            int col = n == 0 ? 0xFF1E9E3A : Round.isRed(n) ? 0xFFC62828 : 0xFF202020;
            int bx = x + w - 52, by = y + 28;
            g.fill(bx - 1, by - 1, bx + 41, by + 41, 0xFFFFFFFF);
            g.fill(bx, by, bx + 40, by + 40, col);
            g.pose().pushMatrix();
            g.pose().translate(bx + 20, by + 12);
            g.pose().scale(2f, 2f);
            String ns = String.valueOf(n);
            text(g, f, ns, -f.width(ns) / 2, 0, 0xFFFFFF);
            g.pose().popMatrix();
        } else {
            text(g, f, "Viewer: " + hand(r.viewerHand, false), x + 8, y + 38, 0x55FFFF);
            text(g, f, "Host:   " + hand(r.hostHand, r.hostCardHidden()), x + 8, y + 52, 0xFFAA00);
        }

        if (r.outcome != null) {
            String t; int c; String sub;
            switch (r.outcome) {
                case VIEWER -> { t = "VIEWER WON!"; c = 0x55FF55; sub = r.viewer + " wins $" + CasinoHostClient.fmt(r.payout()); }
                case HOST -> { t = "HOST WON!"; c = 0xFFAA00; sub = "The house takes $" + CasinoHostClient.fmt(r.bet); }
                default -> { t = "PUSH"; c = 0xFFFFFF; sub = "Bet returned"; }
            }
            g.pose().pushMatrix();
            g.pose().translate(sw / 2f, y + h + 14);
            g.pose().scale(3f, 3f);
            text(g, f, t, -f.width(t) / 2, 0, c);
            g.pose().popMatrix();
            text(g, f, sub, sw / 2 - f.width(sub) / 2, y + h + 44, 0xFFFFFF);
        }
    }

    static String hand(List<Integer> h, boolean hideSecond) {
        if (h.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.size(); i++) sb.append(hideSecond && i == 1 ? "?" : Round.card(h.get(i))).append(' ');
        return sb + (hideSecond ? "" : "(" + Round.total(h) + ")");
    }

    // ---------- confetti ----------
    static void drawConfetti(GuiGraphicsExtractor g, int sw, int sh, float dt) {
        int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFAA00};
        if (Fx.confettiPending) {
            Fx.confettiPending = false;
            for (int i = 0; i < 220; i++) {
                float cx = sw / 2f + (Fx.R.nextFloat() - 0.5f) * sw * 0.3f;
                double ang = Fx.R.nextDouble() * Math.PI * 2, sp = 1 + Fx.R.nextFloat() * 5;
                Fx.parts.add(new float[]{cx, sh * 0.35f, (float) (Math.cos(ang) * sp), (float) (Math.sin(ang) * sp) - 2f,
                        Fx.R.nextInt(colors.length)});
            }
        }
        var it = Fx.parts.iterator();
        while (it.hasNext()) {
            float[] p = it.next();
            p[0] += p[2] * dt; p[1] += p[3] * dt;
            p[3] += 0.12f * dt; // gravity
            p[2] *= 0.99f;
            if (p[1] > sh + 10) { it.remove(); continue; }
            int x = (int) p[0], y = (int) p[1];
            g.fill(x, y, x + 3, y + 3, colors[(int) p[4]]);
        }
    }
}
