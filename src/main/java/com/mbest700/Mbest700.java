package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.component.DataComponentTypes;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    private static long lastCrystalTime, lastAnchorTime, lastShieldTime = 0;

    // Renkler
    public static final int COLOR_AMETHYST = 0xFFA020F0;
    public static final int COLOR_BG = 0xEE111111;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", "Combat")
            .addSetting("Range", 5.0, 3.0, 6.0)
            .addSetting("Delay", 10.0, 1.0, 50.0));
        addMod(new Module("AutoAnchor", "Combat")
            .addSetting("Delay", 100.0, 20.0, 500.0));
        addMod(new Module("ShieldCracker", "Combat"));
        addMod(new Module("Reach", "Combat")
            .addSetting("Dist", 4.2, 3.0, 6.0));
        addMod(new Module("FullBright", "Render"));
        addMod(new Module("FastXP", "Player"));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (getMod("FullBright").enabled) mc.options.getGamma().setValue(100.0);
        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled) doAutoAnchor();
    }

    private static void doAutoCrystal() {
        Module m = getMod("AutoCrystal");
        long delay = (long) (1000 / m.getSetting("Delay").val);
        if (System.currentTimeMillis() - lastCrystalTime < delay) return;
        
        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.canSee(e) && mc.player.distanceTo(crystal) < m.getSetting("Range").val) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = System.currentTimeMillis();
            }
        });
    }

    private static void doAutoAnchor() {
        Module m = getMod("AutoAnchor");
        if (System.currentTimeMillis() - lastAnchorTime < m.getSetting("Delay").val) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) return;
        
        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);

        if (anc != -1 && glow != -1) {
            int old = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = anc;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            mc.player.getInventory().selectedSlot = glow;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            mc.player.getInventory().selectedSlot = old;
            lastAnchorTime = System.currentTimeMillis();
        }
    }

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - lastShieldTime < 600) return;
        if (mc.targetedEntity instanceof PlayerEntity target && target.isHolding(Items.SHIELD)) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) axe = i;
            if (axe != -1) {
                int old = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = axe;
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.getInventory().selectedSlot = old;
                lastShieldTime = System.currentTimeMillis();
            }
        }
    }

    public static Module getMod(String name) { return moduleMap.get(name); }
    private static int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static class Module {
        public String name, category;
        public boolean enabled = false;
        public Map<String, Setting> settings = new LinkedHashMap<>();
        public Module(String n, String c) { name = n; category = c; }
        public Module addSetting(String n, double v, double min, double max) {
            settings.put(n, new Setting(n, v, min, max)); return this;
        }
        public Setting getSetting(String n) { return settings.get(n); }
        public void toggle() { enabled = !enabled; }
    }

    public static class Setting {
        public String name; public double val, min, max;
        public Setting(String n, double v, double min, double max) { name = n; val = v; this.min = min; this.max = max; }
    }

    public static class AmethystGui extends Screen {
        public AmethystGui() { super(Text.literal("Amethyst")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(20, 20, 200, 250, COLOR_BG);
            context.drawText(this.textRenderer, "§d§lMBEST700", 30, 30, 0xFFFFFF, true);
            int y = 50;
            for (Module m : moduleMap.values()) {
                int col = m.enabled ? COLOR_AMETHYST : 0xFF888888;
                context.fill(30, y, 40, y + 10, col);
                context.drawText(this.textRenderer, m.name, 45, y + 1, 0xFFFFFF, false);
                y += 15;
                if (m.enabled) {
                    for (Setting s : m.settings.values()) {
                        context.drawText(this.textRenderer, " - " + s.name + ": " + String.format("%.1f", s.val), 55, y, 0xAAAAAA, false);
                        y += 12;
                    }
                }
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 50;
            for (Module m : moduleMap.values()) {
                if (mouseX > 30 && mouseX < 150 && mouseY > y && mouseY < y + 12) {
                    if (button == 0) m.toggle();
                    return true;
                }
                y += 15;
                if (m.enabled) {
                    for (Setting s : m.settings.values()) {
                        if (mouseX > 55 && mouseX < 180 && mouseY > y && mouseY < y + 10) {
                            s.val = (s.val >= s.max) ? s.min : s.val + 0.5;
                            return true;
                        }
                        y += 12;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
