package dev.example.casinohost;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CasinoHostClient implements ClientModInitializer {

    public static final long MIN_PAY = 1_000_000L;

    // !!! Adjust to DonutSMP's real payment message (send yourself a payment and look at chat) !!!
    static final Pattern PAID = Pattern.compile("(\\w{3,16}) (?:paid|sent) you \\$?([\\d,.]+[KkMmBb]?)");

    /** total paid per player */
    public static final Map<String, Long> totals = new LinkedHashMap<>();
    public static Round.Type mode = Round.Type.ROULETTE;
    public static boolean auto = false;
    public static boolean autoPay = false; // OFF by default: sends /pay to winners when ON
    public static Round round;

    static final Deque<String> cmdQueue = new ArrayDeque<>();
    static KeyMapping menuKey;
    static int cmdCooldown, autoCooldown, fxTicks;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("casinohost", "main"));
        menuKey = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.casinohost.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));

        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay) return;
            Matcher m = PAID.matcher(msg.getString());
            if (m.find()) {
                long amt = parseAmount(m.group(2));
                if (amt > 0) totals.merge(m.group(1), amt, Long::sum);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("casinohost", "overlay"), Hud::extract);
    }

    /** Players with 1M+ paid, sorted lowest -> highest. */
    public static List<Map.Entry<String, Long>> listed() {
        List<Map.Entry<String, Long>> l = new ArrayList<>();
        for (var e : totals.entrySet()) if (e.getValue() >= MIN_PAY) l.add(Map.entry(e.getKey(), e.getValue()));
        l.sort(Map.Entry.comparingByValue());
        return l;
    }

    public static void startNext() {
        if (round != null) return;
        var l = listed();
        if (l.isEmpty()) return;
        var first = l.get(0); // lowest payer first
        totals.remove(first.getKey());
        round = new Round(mode, first.getKey(), first.getValue());
    }

    void tick(Minecraft mc) {
        while (menuKey.consumeClick()) mc.setScreenAndShow(new MenuScreen());
        if (mc.player == null) return;

        if (round != null) {
            round.tick();
            if (round.outcome != null && !round.announced) {
                round.announced = true;
                if (round.outcome != Round.Outcome.PUSH) {
                    fxTicks = 80;
                    Hud.Fx.confettiPending = true;
                    mc.player.playSound(SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1f);
                }
                if (autoPay && round.payout() > 0) cmdQueue.add("pay " + round.viewer + " " + round.payout());
            }
            if (round.done()) round = null;
        } else if (auto && !listed().isEmpty()) {
            if (++autoCooldown >= 60) { autoCooldown = 0; startNext(); }
        }

        if (fxTicks > 0) {
            if (fxTicks % 8 == 0) worldBurst(mc);
            fxTicks--;
        }

        if (cmdCooldown > 0) cmdCooldown--;
        else if (!cmdQueue.isEmpty() && mc.getConnection() != null) {
            mc.getConnection().sendCommand(cmdQueue.poll());
            cmdCooldown = 40;
        }
    }

    /** Client-side firework burst in front of the player. */
    static void worldBurst(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        Random r = new Random();
        Vec3 look = mc.player.getViewVector(1f);
        double cx = mc.player.getX() + look.x * 6 + (r.nextDouble() - 0.5) * 6;
        double cy = mc.player.getEyeY() + 2 + r.nextDouble() * 3;
        double cz = mc.player.getZ() + look.z * 6 + (r.nextDouble() - 0.5) * 6;
        for (int i = 0; i < 60; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(0.35);
            mc.level.addParticle(ParticleTypes.FIREWORK, cx, cy, cz, d.x, d.y, d.z);
            if (i % 3 == 0) mc.level.addParticle(ParticleTypes.END_ROD, cx, cy, cz, d.x * 0.6, d.y * 0.6, d.z * 0.6);
        }
    }

    public static long parseAmount(String s) {
        try {
            s = s.replace(",", "").toUpperCase();
            long mult = 1;
            char last = s.charAt(s.length() - 1);
            if (last == 'K') mult = 1_000L;
            else if (last == 'M') mult = 1_000_000L;
            else if (last == 'B') mult = 1_000_000_000L;
            if (mult != 1) s = s.substring(0, s.length() - 1);
            return (long) (Double.parseDouble(s) * mult);
        } catch (Exception e) { return -1; }
    }

    public static String fmt(long n) {
        if (n >= 1_000_000_000L) return trim(n / 1_000_000_000.0) + "B";
        if (n >= 1_000_000L) return trim(n / 1_000_000.0) + "M";
        if (n >= 1_000L) return trim(n / 1_000.0) + "K";
        return String.valueOf(n);
    }
    private static String trim(double d) { return d == Math.floor(d) ? String.valueOf((long) d) : String.format("%.1f", d); }
}
