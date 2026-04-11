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
import net.minecraft.component.DataComponentTypes;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    // Zamanlayıcılar (Delay Yönetimi)
    private static long crystalTimer, anchorTimer, shieldTimer, swordTimer = 0;
    private static int anchorStep = 0; // 0: Koy, 1: Doldur, 2: Patlat

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        // --- COMBAT ---
        addMod(new Module("AutoCrystal", "Combat")
            .addSetting("Speed", 10.0, 1.0, 20.0)); // Vuruş hızı ayarı

        addMod(new Module("AutoAnchor", "Combat")
            .addSetting("StepDelay", 0.2, 0.1, 1.0)); // İstediğin 0.2sn gecikme

        addMod(new Module("ShieldCracker", "Combat"));
        
        addMod(new Module("AutoSwordHit", "Combat")
            .addSetting("LookTime", 0.5, 0.1, 2.0)); // Bakma süresi ayarı

        addMod(new Module("Velocity", "Combat")
            .addSetting("Reduce", 100.0, 0.0, 100.0)); // %100 = Sıfır KB

        // --- PLAYER ---
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("FullBright", "Render"));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (getMod("SmartTotem").enabled) doSmartTotem();
        if (getMod("FullBright").enabled) mc.options.getGamma().setValue(100.0);
        if (getMod("Velocity").enabled && mc.player.hurtTime > 0) {
            double reduce = getMod("Velocity").getSetting("Reduce").val / 100.0;
            mc.player.setVelocity(mc.player.getVelocity().multiply(1.0 - reduce, 1.0, 1.0 - reduce));
        }
        
        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        if (getMod("AutoAnchor").enabled) doAutoAnchor();
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

    private static void doAutoAnchor() {
        double delay = getMod("AutoAnchor").getSetting("StepDelay").val * 1000;
        if (System.currentTimeMillis() - anchorTimer < delay) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) return;

        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);

        if (anc != -1 && glow != -1) {
            switch (anchorStep) {
                case 0: // Anchor Koy
                    mc.player.getInventory().selectedSlot = anc;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorStep = 1;
                    break;
                case 1: // Glowstone Doldur
                    mc.player.getInventory().selectedSlot = glow;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorStep = 2;
                    break;
                case 2: // Patlat
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorStep = 0;
                    break;
            }
            anchorTimer = System.currentTimeMillis();
        }
    }

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - shieldTimer < 1000) return;
        // Sadece rakip kalkan tutuyorsa çalışır
        if (mc.targetedEntity instanceof PlayerEntity target && target.isHolding(Items.SHIELD)) {
            int axe = findAxe();
            if (axe != -1) {
                int old = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = axe;
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.getInventory().selectedSlot = old;
                shieldTimer = System.currentTimeMillis();
            }
        }
    }

    private static void doAutoSwordHit() {
        double lookDelay = getMod("AutoSwordHit").getSetting("LookTime").val * 1000;
        if (mc.targetedEntity instanceof PlayerEntity target) {
            if (swordTimer == 0) swordTimer = System.currentTimeMillis();
            
            if (System.currentTimeMillis() - swordTimer >= lookDelay) {
                int sword = findSword();
                if (sword != -1) {
                    int old = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = sword;
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.getInventory().selectedSlot = old; // D-Tap için eski eşyaya dön
                    swordTimer = 0; // Reset
                }
            }
        } else {
            swordTimer = 0;
        }
    }

    private static void doSmartTotem() {
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = -1;
            for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) slot = i;
            if (slot != -1) mc.interactionManager.clickSlot(0, slot < 9 ? slot + 36 : slot, 45, SlotActionType.SWAP, mc.player);
        }
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
    }

    // --- YARDIMCI ---
    public static Module getMod(String name) { return moduleMap.get(name); }
    private static int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static int findAxe() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        return -1;
    }
    private static int findSword() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof SwordItem) return i;
        return -1;
    }

    // --- MODÜL SİSTEMİ ---
    public static class Module {
        public String name, category; public boolean enabled = false;
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

    // --- GUI ---
    public static class AmethystGui extends Screen {
        public AmethystGui() { super(Text.literal("Amethyst")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(10, 10, 180, 260, 0xEE000000);
            context.drawText(this.textRenderer, "§dMbest700 §7v4", 20, 20, 0xFFFFFF, true);
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
                            s.val = (s.val >= s.max) ? s.min : s.val + (s.max - s.min) / 10.0;
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
    
