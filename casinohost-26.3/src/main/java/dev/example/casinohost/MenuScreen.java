package dev.example.casinohost;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class MenuScreen extends Screen {
    private Button modeBtn, autoBtn, payBtn;

    public MenuScreen() { super(Component.literal("Casino Host Menu")); }

    @Override
    protected void init() {
        int cx = width / 2 - 100, y = height / 4, gap = 24;

        modeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CasinoHostClient.mode = CasinoHostClient.mode == Round.Type.ROULETTE ? Round.Type.BLACKJACK : Round.Type.ROULETTE;
            refresh();
        }).bounds(cx, y, 200, 20).build());

        autoBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CasinoHostClient.auto = !CasinoHostClient.auto;
            refresh();
        }).bounds(cx, y + gap, 200, 20).build());

        payBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            CasinoHostClient.autoPay = !CasinoHostClient.autoPay;
            refresh();
        }).bounds(cx, y + gap * 2, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Play next now (lowest payer)"),
                b -> CasinoHostClient.startNext()).bounds(cx, y + gap * 3, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Add 3 test payers"), b -> {
            Random r = new Random();
            for (String n : new String[]{"Steve", "Alex", "Notch"})
                CasinoHostClient.totals.merge(n, 1_000_000L + r.nextInt(9) * 500_000L, Long::sum);
        }).bounds(cx, y + gap * 4, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Clear payer list"),
                b -> CasinoHostClient.totals.clear()).bounds(cx, y + gap * 5, 200, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx, y + gap * 6 + 6, 200, 20).build());

        refresh();
    }

    private void refresh() {
        modeBtn.setMessage(Component.literal("Game: " + CasinoHostClient.mode));
        autoBtn.setMessage(Component.literal("Auto-run next player: " + (CasinoHostClient.auto ? "ON" : "OFF")));
        payBtn.setMessage(Component.literal("Auto /pay winners: " + (CasinoHostClient.autoPay ? "ON" : "OFF")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        String t = title.getString();
        g.text(font, t, width / 2 - font.width(t) / 2, height / 4 - 16, 0xFFFFFFFF, true);
        String note = "Roulette: 18/37 for viewer, 1:1  |  Blackjack: both auto, BJ pays 3:2";
        g.text(font, note, width / 2 - font.width(note) / 2, height - 24, 0xFFAAAAAA, true);
    }
}
