package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    private static long crystalTimer, anchorTimer, shieldTimer, swordTimer, xpTimer = 0;
    private static int anchorStep = -1; 

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", "Combat").addSetting("Speed", 35.0, 1.0, 60.0));
        addMod(new Module("AutoAnchor", "Combat"));
        addMod(new Module("ShieldCracker", "Combat"));
        addMod(new Module("AutoSwordHit", "Combat").addSetting("LookTime", 0.5, 0.1, 2.0));
        addMod(new Module("Velocity", "Combat").addSetting("Reduce", 100.0, 0.0, 100.0));
        addMod(new Module("TotemCounter", "Render")); 
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("FastXP", "Player").addSetting("Speed", 20.0, 1.0, 20.0));
        addMod(new Module("FullBright", "Render"));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        // --- FULLBRIGHT FIX ---
        if (getMod("FullBright").enabled) {
            mc.options.getGamma().setValue(100.0);
        }

        // --- SMART TOTEM (AUTO TOTEM) ---
        if (getMod("SmartTotem").enabled) {
            if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                int totemSlot = findItemInventory(Items.TOTEM_OF_UNDYING);
                if (totemSlot != -1) {
                    mc.interactionManager.clickSlot(0, totemSlot, 45, SlotActionType.SWAP, mc.player);
                }
            }
        }

        // --- FAST XP ---
        if (getMod("FastXP").enabled && mc.options.useKey.isPressed() && mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            double speed = getMod("FastXP").getSetting("Speed").val;
            if (System.currentTimeMillis() - xpTimer >= (1000 / speed)) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                xpTimer = System.currentTimeMillis();
            }
        }

        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        if (anchorStep != -1) doAutoAnchorSequence();
    }

    private static void doAutoCrystal() {
        double speed = getMod("AutoCrystal").getSetting("Speed").val;
        if (System.currentTimeMillis() - crystalTimer < (1000 / speed)) return;
        
        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.canSee(e) && mc.player.distanceTo(crystal) < 5.0) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                crystalTimer = System.currentTimeMillis();
            }
        });
    }

    private static void doAutoAnchorSequence() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) { anchorStep = -1; return; }

        int anc = findItemHotbar(Items.RESPAWN_ANCHOR);
        int glow = findItemHotbar(Items.GLOWSTONE);
        if (anc == -1 || glow == -1) { anchorStep = -1; return; }

        long now = System.currentTimeMillis();
        switch (anchorStep) {
            case 0: // Anchor Koy
                mc.player.getInventory().selectedSlot = anc;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                anchorTimer = now; anchorStep = 1; break;
            case 1: // 0.2sn -> Glowstone
                if (now - anchorTimer >= 200) {
                    mc.player.getInventory().selectedSlot = glow;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorTimer = now; anchorStep = 2;
                } break;
            case 2: // 0.1sn -> Patlat (Totem Öncelikli)
                if (now - anchorTimer >= 100) {
                    int totem = findItemHotbar(Items.TOTEM_OF_UNDYING);
                    mc.player.getInventory().selectedSlot = (totem != -1) ? totem : anc;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorStep = -1; 
                } break;
        }
    }

    private static void doAutoSwordHit() {
        if (mc.targetedEntity instanceof PlayerEntity target) {
            double lookDelay = getMod("AutoSwordHit").getSetting("LookTime").val * 1000;
            if (swordTimer == 0) swordTimer = System.currentTimeMillis();
            if (System.currentTimeMillis() - swordTimer >= lookDelay) {
                int sword = findItemHotbar(Items.NETHERITE_SWORD);
                if (sword == -1) sword = findItemHotbar(Items.DIAMOND_SWORD);
                if (sword != -1) {
                    int old = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = sword;
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.getInventory().selectedSlot = old;
                    swordTimer = 0;
                }
            }
        } else { swordTimer = 0; }
    }

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - shieldTimer < 1000) return;
        if (mc.targetedEntity instanceof PlayerEntity target && target.isHolding(Items.SHIELD)) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) axe = i;
            if (axe != -1) {
                int old = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = axe;
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.getInventory().selectedSlot = old;
                shieldTimer = System.currentTimeMillis();
            }
        }
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled && anchorStep == -1) {
            anchorStep = 0;
        }
    }

    public static Module getMod(String name) { return moduleMap.get(name); }
    
    private static int findItemHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private static int findItemInventory(net.minecraft.item.Item item) {
        for (int i = 9; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static class Module {
        public String name, category;
        public boolean enabled = false;
        public Map<String, Setting> settings = new LinkedHashMap<>();
        public Module(String n, String c) { this.name = n; this.category = c; }
        public Module addSetting(String n, double v, double min, double max) {
            settings.put(n, new Setting(n, v, min, max)); return this;
        }
        public Setting getSetting(String n) { return settings.get(n); }
        public void toggle() { enabled = !enabled; }
    }

    public static class Setting {
        public String name; public double val, min, max;
        public Setting(String n, double v, double min, double max) { this.name = n; this.val = v; this.min = min; this.max = max; }
    }

    public static class AmethystGui extends Screen {
        public AmethystGui() { super(Text.literal("Amethyst")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(10, 10, 180, 260, 0xEE000000);
            context.drawText(this.textRenderer, "§dMbest700 §7v5", 20, 20, 0xFFFFFF, true);
            int y = 40;
            for (Module m : moduleMap.values()) {
                int col = m.enabled ? 0xFFA020F0 : 0xFF555555;
                context.fill(20, y, 30, y + 10, col);
                context.drawText(this.textRenderer, m.name, 35, y + 1, 0xFFFFFF, false);
                y += 15;
                if (m.enabled) {
                    for (Setting s : m.settings.values()) {
                        context.drawText(this.textRenderer, " > " + s.name + ": " + String.format("%.1f", s.val), 40, y, 0xAAAAAA, false);
                        y += 12;
                    }
                }
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 40;
            for (Module m : moduleMap.values()) {
                if (mouseX > 20 && mouseX < 150 && mouseY > y && mouseY < y + 12) {
                    m.toggle(); return true;
                }
                y += 15;
                if (m.enabled) {
                    for (Setting s : m.settings.values()) {
                        if (mouseX > 40 && mouseX < 150 && mouseY > y && mouseY < y + 10) {
                            s.val = (s.val >= s.max) ? s.min : s.val + 2.0;
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
