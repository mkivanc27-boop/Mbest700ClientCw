package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.component.DataComponentTypes;

import java.util.LinkedHashMap;
import java.util.Map;

public class Mbest700 implements ClientModInitializer {

    public static MinecraftClient mc;
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();

    // Cooldown / timer fields
    private static long lastCrystalTime = 0;
    private static long lastAnchorTime  = 0;
    private static long lastSurroundTime = 0;

    // ─────────────────────────────────────────────────────────────
    // Inner class: Module
    // ─────────────────────────────────────────────────────────────
    public static class Module {
        public String name;
        public String category;
        public boolean enabled;
        public double delay; 

        public Module(String name, String category, double delay) {
            this.name     = name;
            this.category = category;
            this.enabled  = false;
            this.delay    = delay;
        }

        public void toggle() {
            enabled = !enabled;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Inner class: MeteorGui  (GUI MENÜSÜ - GERİ EKLENDİ)
    // ─────────────────────────────────────────────────────────────
    public static class MeteorGui extends Screen {

        private static final String[] cats = {"Combat", "Player", "Render"};

        public MeteorGui() {
            super(Text.of("Mbest700 Meteor"));
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            super.render(context, mouseX, mouseY, delta);

            int colWidth = 110;
            int startX   = 8;
            int startY   = 8;
            int rowH     = 12;

            for (int c = 0; c < cats.length; c++) {
                String cat = cats[c];
                int x      = startX + c * colWidth;
                int y      = startY;

                context.drawText(mc.textRenderer, cat, x, y, 0xFFFFFF, true);
                y += rowH + 2;

                for (Module mod : moduleMap.values()) {
                    if (!mod.category.equals(cat)) continue;

                    int bgColor = mod.enabled ? 0xAA00AA00 : 0xAA333333;
                    int textColor = mod.enabled ? 0xFF00FF00 : 0xFFAAAAAA;

                    context.fill(x, y - 1, x + colWidth - 4, y + rowH - 1, bgColor);

                    String label = mod.name + (mod.enabled ? " §a[Aç]" : " §7[Kapat]");
                    context.drawText(mc.textRenderer, label, x + 2, y, textColor, false);

                    y += rowH;
                }
            }

            Module reach = getMod("Reach");
            if (reach != null && reach.enabled) {
                String info = "Reach: " + String.format("%.1f", reach.delay) + "  "
                    + "[Sağ Tık: Ayar Artır]  [V: AutoAnchor]";
                context.drawText(mc.textRenderer, info, startX, height - 14, 0xFFFFAA00, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int colWidth = 110;
            int startX   = 8;
            int startY   = 8 + 14; 
            int rowH     = 12;

            for (int c = 0; c < cats.length; c++) {
                String cat = cats[c];
                int colX   = startX + c * colWidth;
                int y      = startY;

                for (Module mod : moduleMap.values()) {
                    if (!mod.category.equals(cat)) continue;

                    if (mouseX >= colX && mouseX < colX + colWidth - 4 && mouseY >= y - 1 && mouseY < y + rowH - 1) {
                        if (button == 0) {
                            mod.toggle();
                        } else if (button == 1) {
                            mod.delay = Math.min(mod.delay + 0.05, 5.0);
                        }
                        return true;
                    }
                    y += rowH;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean shouldCloseOnEsc() { return true; }

        public boolean isPauseScreen() { return false; }
    }

    // ─────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────
    @Override
    public void onInitializeClient() {
        mc = MinecraftClient.getInstance();
        init();
    }

    private static void init() {
        // Combat
        addMod(new Module("AutoCrystal",   "Combat", 0.05));
        addMod(new Module("AutoAnchor",    "Combat", 0.1));
        addMod(new Module("Surround",      "Combat", 0.05));
        addMod(new Module("AutoTotem",     "Combat", 0.0));
        addMod(new Module("ShieldCracker", "Combat", 0.2));
        addMod(new Module("Reach",         "Combat", 3.5)); // GUI değeri

        // Player
        addMod(new Module("AutoEat",       "Player", 0.1));
        addMod(new Module("Velocity",      "Player", 0.0));
        addMod(new Module("FastXP",        "Player", 0.1));

        // Render
        addMod(new Module("StorageESP",    "Render", 0.0));
        addMod(new Module("FullBright",    "Render", 0.0));
    }

    private static void addMod(Module mod) { moduleMap.put(mod.name, mod); }
    public static Module getMod(String name) { return moduleMap.get(name); }

    // ─────────────────────────────────────────────────────────────
    // TİCK METODU
    // ─────────────────────────────────────────────────────────────
    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (getMod("AutoCrystal") != null && getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("AutoAnchor") != null && getMod("AutoAnchor").enabled)  doAutoAnchor();
        if (getMod("Surround") != null && getMod("Surround").enabled)    doSurround();
        if (getMod("AutoTotem") != null && getMod("AutoTotem").enabled)   doAutoTotem();
        if (getMod("AutoEat") != null && getMod("AutoEat").enabled)     doAutoEat();
    }

    // ─────────────────────────────────────────────────────────────
    // EKSİK OLAN ONKEY METODU (BURASI HATA VERİYORDU)
    // ─────────────────────────────────────────────────────────────
    public static void onKey(int key, int action) {
        if (action != 1) return; // Sadece basıldığında (GLFW_PRESS) çalışsın

        // R = 82 → Menüyü (GUI) aç / kapat
        if (key == 82) {
            if (mc.currentScreen instanceof MeteorGui) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new MeteorGui());
            }
            return;
        }

        // V = 86 → AutoAnchor geçiş yap
        if (key == 86) {
            Module anchor = getMod("AutoAnchor");
            if (anchor != null) anchor.toggle();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // CPVP METOTLARI
    // ─────────────────────────────────────────────────────────────

    private static void doAutoCrystal() {
        long now = System.currentTimeMillis();
        if (now - lastCrystalTime < (getMod("AutoCrystal").delay * 1000)) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal && mc.player.distanceTo(crystal) <= 6) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = now;
                break;
            }
        }
    }

    private static void doSurround() {
        long now = System.currentTimeMillis();
        if (now - lastSurroundTime < 50) return;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos[] surroundBlocks = {
            playerPos.north(), playerPos.south(), playerPos.east(), playerPos.west()
        };

        int obbySlot = findItem(Items.OBSIDIAN);
        if (obbySlot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        
        for (BlockPos pos : surroundBlocks) {
            if (mc.world.getBlockState(pos).isReplaceable()) {
                switchToSlot(obbySlot);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, 
                    new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastSurroundTime = now;
            }
        }
        switchToSlot(oldSlot);
    }

    private static void doAutoTotem() {
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        int totemSlot = findItem(Items.TOTEM_OF_UNDYING);
        if (totemSlot != -1) {
            int slotIndex = totemSlot < 9 ? totemSlot + 36 : totemSlot;
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private static void doAutoAnchor() {
        // İleride geliştirilecek
    }

    private static void switchToSlot(int slot) {
        if (slot != -1 && slot < 9) {
            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private static int findItem(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private static void doAutoEat() {
        if (mc.player.getHungerManager().getFoodLevel() <= 16) {
            int foodSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).contains(DataComponentTypes.FOOD)) {
                    foodSlot = i;
                    break;
                }
            }
            if (foodSlot != -1) {
                mc.player.getInventory().selectedSlot = foodSlot;
                mc.options.useKey.setPressed(true);
            }
        } else {
            mc.options.useKey.setPressed(false);
        }
    }
}
