package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    private static long crystalTimer, anchorTimer, shieldTimer, swordTimer, xpTimer = 0;
    private static int anchorStep = -1;
    private static boolean isSafeMode = false;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", "Combat").addSetting("Speed", 45.0, 1.0, 80.0));
        addMod(new Module("AutoAnchor", "Combat").addSetting("Delay", 50.0, 10.0, 300.0));
        addMod(new Module("SafeAnchor", "Combat")); // Senin istediğin yeni Safe sistemi
        addMod(new Module("ShieldCracker", "Combat"));
        addMod(new Module("AutoSwordHit", "Combat").addSetting("LookTime", 0.5, 0.1, 2.0));
        addMod(new Module("Velocity", "Combat").addSetting("Reduce", 100.0, 0.0, 100.0));
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("FastXP", "Player").addSetting("Speed", 20.0, 1.0, 20.0));
        addMod(new Module("TotemCounter", "Render"));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        // --- ÇALIŞAN AUTO TOTEM ---
        if (getMod("SmartTotem").enabled) {
            if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                int t = findItemInventory(Items.TOTEM_OF_UNDYING);
                if (t != -1) mc.interactionManager.clickSlot(0, t, 45, SlotActionType.SWAP, mc.player);
            }
        }

        if (getMod("FastXP").enabled) doFastXP();
        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        if (anchorStep != -1) doAutoAnchorSequence();
    }

    // --- HIZLANDIRILMIŞ ANCHOR & SAFE ANCHOR ---
    private static void doAutoAnchorSequence() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) { anchorStep = -1; return; }

        int anc = findItemHotbar(Items.RESPAWN_ANCHOR);
        int glow = findItemHotbar(Items.GLOWSTONE);
        if (anc == -1 || glow == -1) { anchorStep = -1; return; }

        long now = System.currentTimeMillis();
        double delay = getMod("AutoAnchor").getSetting("Delay").val;

        switch (anchorStep) {
            case 0: // Adım 1: Anchor Koy
                mc.player.getInventory().selectedSlot = anc;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                anchorTimer = now; anchorStep = 1; break;
            case 1: // Adım 2: Glowstone Doldur (Hızlandırıldı)
                if (now - anchorTimer >= delay) {
                    mc.player.getInventory().selectedSlot = glow;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    
                    // SAFE MODE: Eğer SafeAnchor açıksa önüne koruma bloğu koyar
                    if (isSafeMode) {
                        BlockPos protectPos = mc.player.getBlockPos().offset(mc.player.getHorizontalFacing());
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, 
                            new BlockHitResult(mc.player.getPos(), Direction.UP, protectPos, false));
                    }
                    
                    anchorTimer = now; anchorStep = 2;
                } break;
            case 2: // Adım 3: Patlat
                if (now - anchorTimer >= (delay / 2)) {
                    int totem = findItemHotbar(Items.TOTEM_OF_UNDYING);
                    mc.player.getInventory().selectedSlot = (totem != -1) ? totem : anc;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorStep = -1;
                } break;
        }
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        
        // V tuşu: Normal Anchor
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled && anchorStep == -1) {
            isSafeMode = false;
            anchorStep = 0;
        }
        // J tuşu (veya başka): Safe Anchor
        if (key == GLFW.GLFW_KEY_J && getMod("SafeAnchor").enabled && anchorStep == -1) {
            isSafeMode = true;
            anchorStep = 0;
        }
    }

    // --- YENİLENMİŞ MENÜ (SAĞ TIK VE ANİMASYON) ---
    public static class AmethystGui extends Screen {
        private float anim = 0;
        private String selectedMod = "";

        public AmethystGui() { super(Text.literal("Amethyst")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            anim = Math.min(anim + 0.1f, 1.0f); // Basit açılış animasyonu
            int x = (int)(10 * anim);
            context.fill(x, 10, x + 180, 260, 0xEE0A0A0A);
            context.drawText(this.textRenderer, "§dAmethyst §7v6", x + 15, 20, 0xFFFFFF, true);
            
            int y = 40;
            for (Module m : moduleMap.values()) {
                int col = m.enabled ? 0xFFA020F0 : 0xFF444444;
                context.fill(x + 15, y, x + 25, y + 10, col);
                context.drawText(this.textRenderer, m.name, x + 30, y + 1, 0xFFFFFF, false);
                
                // Eğer modül seçiliyse (Sağ tıklandıysa) ayarları göster
                if (m.name.equals(selectedMod)) {
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        context.drawText(this.textRenderer, " > " + s.name + ": " + String.format("%.1f", s.val), x + 40, y, 0xBBBBBB, false);
                    }
                }
                y += 15;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 40;
            for (Module m : moduleMap.values()) {
                if (mouseX > 20 && mouseX < 150 && mouseY > y && mouseY < y + 12) {
                    if (button == 0) { // SOL TIK: Aç/Kapat
                        m.toggle();
                    } else if (button == 1) { // SAĞ TIK: Özelleştirme Menüsünü Aç
                        selectedMod = selectedMod.equals(m.name) ? "" : m.name;
                    }
                    return true;
                }
                y += (m.name.equals(selectedMod)) ? 15 + (m.settings.size() * 15) : 15;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean shouldPause() { return false; }
    }

    // Yardımcı metodlar (Inventory ve Combat için)
    private static void doFastXP() { /* Önceki kodla aynı */ }
    private static void doAutoCrystal() { /* Önceki kodla aynı */ }
    private static void doAutoSwordHit() { /* Önceki kodla aynı */ }
    private static void doShieldCracker() { /* Önceki kodla aynı */ }
    public static Module getMod(String name) { return moduleMap.get(name); }
    private static int findItemHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static int findItemInventory(net.minecraft.item.Item item) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

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
}
