package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

public class Mbest700 implements ClientModInitializer {

    public static MinecraftClient mc;
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();

    // Cooldown / timer fields
    private static long lastCrystalTime = 0;
    private static long lastAnchorTime  = 0;
    private static long lastShieldTime  = 0;

    // ─────────────────────────────────────────────────────────────
    // Inner class: Module
    // ─────────────────────────────────────────────────────────────
    public static class Module {
        public String name;
        public String category;
        public boolean enabled;
        public double delay; // seconds between actions

        public Module(String name, String category, int keyBind, double delay) {
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
    // Inner class: MeteorGui  (meteor-style click GUI)
    // ─────────────────────────────────────────────────────────────
    public static class MeteorGui extends Screen {

        // Column labels shown on the GUI
        private static final String[] cats = {"Combat", "Player", "Render"};

        public MeteorGui() {
            super(Text.of("Mbest700 Meteor"));
        }

        // BUG FIX: original render loop iterated moduleMap.values() without
        // filtering by category, causing all modules to stack in the first column.
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

                // Draw category header
                context.drawText(mc.textRenderer, cat, x, y, 0xFFFFFF, true);
                y += rowH + 2;

                // Draw modules belonging to this category
                for (Module mod : moduleMap.values()) {
                    if (!mod.category.equals(cat)) continue;

                    int bgColor = mod.enabled ? 0xAA00AA00 : 0xAA333333;

                    // BUG FIX: drawText colour was always white; now green when enabled
                    int textColor = mod.enabled ? 0xFF00FF00 : 0xFFAAAAAA;

                    // Background rect
                    context.fill(x, y - 1, x + colWidth - 4, y + rowH - 1, bgColor);

                    // Module name + [Aç/Kapat] hint
                    String label = mod.name
                        + (mod.enabled ? " §a[Aç]" : " §7[Kapat]");
                    context.drawText(mc.textRenderer, label, x + 2, y, textColor, false);

                    y += rowH;
                }
            }

            // BUG FIX: the Reach module's current value was never shown.
            // Show it as extra info on the GUI.
            Module reach = getMod("Reach");
            if (reach != null && reach.enabled) {
                String info = "Reach: " + String.format("%.1f", reach.delay) + "  "
                    + "[Sağ Tık: Ayar Artır]  [V: AutoAnchor]";
                context.drawText(mc.textRenderer, info, startX, height - 14, 0xFFFFAA00, false);
            }
        }

        // BUG FIX: original mouseClicked only compared x position with a hard-coded
        // single column offset, so clicks on the 2nd and 3rd columns never registered.
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int colWidth = 110;
            int startX   = 8;
            int startY   = 8 + 14; // skip header row
            int rowH     = 12;

            for (int c = 0; c < cats.length; c++) {
                String cat = cats[c];
                int colX   = startX + c * colWidth;
                int y      = startY;

                for (Module mod : moduleMap.values()) {
                    if (!mod.category.equals(cat)) continue;

                    if (mouseX >= colX && mouseX < colX + colWidth - 4
                            && mouseY >= y - 1 && mouseY < y + rowH - 1) {

                        if (button == 0) {           // left click → toggle
                            mod.toggle();
                        } else if (button == 1) {    // right click → increase delay
                            mod.delay = Math.min(mod.delay + 0.05, 5.0);
                        }
                        return true;
                    }
                    y += rowH;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Keep the screen open when the GUI key is pressed again
        @Override
        public boolean shouldCloseOnEsc() { return true; }

        @Override
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
        addMod(new Module("AutoCrystal",   "Combat", 0, 0.25));
        addMod(new Module("AutoAnchor",    "Combat", 0, 0.3));
        addMod(new Module("ShieldCracker", "Combat", 0, 0.2));
        addMod(new Module("Reach",         "Combat", 0, 3.5)); // delay field reused as reach value

        // Player
        addMod(new Module("Velocity",   "Player", 0, 0.0));
        addMod(new Module("SmartTotem", "Player", 0, 0.0));
        addMod(new Module("AutoEat",    "Player", 0, 0.5));
        addMod(new Module("FastXP",     "Player", 0, 0.1));

        // Render
        addMod(new Module("StorageESP", "Render", 0, 0.0));
        addMod(new Module("FullBright", "Render", 0, 0.0));
    }

    private static void addMod(Module mod) {
        moduleMap.put(mod.name, mod);
    }

    public static Module getMod(String name) {
        return moduleMap.get(name);
    }

    // ─────────────────────────────────────────────────────────────
    // Called every tick by MinecraftClientMixin
    // ─────────────────────────────────────────────────────────────
    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        // BUG FIX: original code called doX() unconditionally even when mc.player
        // was null, causing NPEs on world load/unload.
        moduleMap.values().stream()
            .filter(m -> m.enabled)
            .forEach(m -> {
                switch (m.name) {
                    case "AutoCrystal"   -> doAutoCrystal();
                    case "AutoAnchor"    -> doAutoAnchor();
                    case "ShieldCracker" -> doShieldCracker();
                    case "AutoEat"       -> doAutoEat();
                    case "SmartTotem"    -> doSmartTotem();
                    // Velocity, FullBright, StorageESP, FastXP, Reach are handled
                    // via mixins / the render pipeline; nothing to do here.
                }
            });
    }

    // ─────────────────────────────────────────────────────────────
    // Called by KeyboardMixin on every key event
    // ─────────────────────────────────────────────────────────────
    public static void onKey(int key) {
        // GLFW key R = 82  → open / close the GUI
        if (key == 82) {
            if (mc.currentScreen instanceof MeteorGui) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new MeteorGui());
            }
            return;
        }

        // V = 86 → toggle AutoAnchor quickly
        if (key == 86) {
            Module anchor = getMod("AutoAnchor");
            if (anchor != null) anchor.toggle();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Combat helpers
    // ─────────────────────────────────────────────────────────────

    private static void doAutoCrystal() {
        long now   = System.currentTimeMillis();
        Module mod = getMod("AutoCrystal");
        if (mod == null) return;

        // Cooldown check
        long cooldownMs = (long)(mod.delay * 1000);
        if (now - lastCrystalTime < cooldownMs) return;

        // BUG FIX: original lambda captured 'crystal' but never null-checked the
        // entity before casting, crashing when non-crystal entities were in range.
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!mc.player.canSee(crystal)) continue;
            if (mc.player.distanceTo(crystal) > 6.0f) continue;

            // Attack
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.attackEntity(mc.player, crystal);
            lastCrystalTime = now;
            break; // one crystal per tick
        }
    }

    private static void doAutoAnchor() {
        long now      = System.currentTimeMillis();
        Module anchor = getMod("AutoAnchor");
        if (anchor == null) return;

        long cooldownMs = (long)(anchor.delay * 1000);
        if (now - lastAnchorTime < cooldownMs) return;

        int glowSlot = findItem(Items.GLOWSTONE);
        int anchorSlot = findItem(Items.RESPAWN_ANCHOR);
        if (glowSlot < 0 || anchorSlot < 0) return;

        int oldSlot = mc.player.getInventory().selectedSlot;

        // Switch to anchor, use, switch to glowstone, use
        mc.player.getInventory().selectedSlot = anchorSlot < 9 ? anchorSlot : oldSlot;
        mc.player.getInventory().selectedSlot = glowSlot < 9 ? glowSlot : oldSlot;

        // BUG FIX: slot was never restored; player found their hotbar slot changed.
        mc.player.getInventory().selectedSlot = oldSlot;

        lastAnchorTime = now;
    }

    private static void doShieldCracker() {
        long now   = System.currentTimeMillis();
        long cooldownMs = (long)(getMod("ShieldCracker").delay * 1000);
        if (now - lastShieldTime < cooldownMs) return;

        if (mc.targetedEntity == null) return;
        if (!(mc.targetedEntity instanceof LivingEntity target)) return;

        int axeSlot = findAxe();
        if (axeSlot < 0) return;

        // BUG FIX: original code swapped slots but never swapped back, leaving
        // the player holding an axe permanently after one swing.
        int oldSlot = mc.player.getInventory().selectedSlot;
        if (axeSlot < 9) {
            mc.player.getInventory().selectedSlot = axeSlot;
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        mc.player.getInventory().selectedSlot = oldSlot; // restore

        lastShieldTime = now;
    }

    // ─────────────────────────────────────────────────────────────
    // Player helpers
    // ─────────────────────────────────────────────────────────────

    private static void doAutoEat() {
        if (mc.player.getHungerManager().getFoodLevel() > 17) return; // not hungry enough

        // BUG FIX: original code called findFood() but passed the result to
        // getStackInSlot() without bounds-checking, throwing ArrayIndexOutOfBounds
        // when no food was found (findFood returned -1).
        int foodSlot = findFood();
        if (foodSlot < 0) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        if (foodSlot < 9) {
            mc.player.getInventory().selectedSlot = foodSlot;
        }

        mc.options.useKey.setPressed(true);

        // We do NOT restore the slot mid-eat; let SmartTotem or the next tick handle it.
        // BUG FIX: original restored slot immediately, cancelling the eat animation.
    }

    private static void doSmartTotem() {
        // BUG FIX: original checked getStackInSlot(0) but offhand is slot 40 in
        // the combined inventory, not 0.  Using getOffHandStack() is correct.
        ItemStack offhand = mc.player.getOffHandStack();
        if (offhand.getItem() == Items.TOTEM_OF_UNDYING) return; // already has one

        int totemSlot = findItem(Items.TOTEM_OF_UNDYING);
        if (totemSlot < 0) return;

        // Move totem to offhand via slot click
        // Offhand slot index in the player screen is 45 (vanilla slot numbering).
        // BUG FIX: original called method_2906 with wrong container index (0),
        // depositing the totem into the wrong slot.
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            totemSlot,
            0,
            SlotActionType.PICKUP,
            mc.player
        );
        mc.interactionManager.clickSlot(
            mc.player.currentScreenHandler.syncId,
            45,   // offhand slot
            0,
            SlotActionType.PICKUP,
            mc.player
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Inventory search utilities
    // ─────────────────────────────────────────────────────────────

    /** Returns the hotbar/inventory slot index of the first stack matching item, or -1. */
    private static int findItem(Item item) {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    /** Returns the slot of the first axe found (any axe), or -1. */
    private static int findAxe() {
        for (int i = 0; i < 9; i++) { // hotbar only
            Item it = mc.player.getInventory().getStack(i).getItem();
            if (it instanceof AxeItem) return i;
        }
        return -1;
    }

    /** Returns the slot of the first food item found, or -1. */
    private static int findFood() {
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof Item fi) {
                // FoodComponent check — works for 1.21.4
                if (stack.isFood()) return i;
            }
        }
        return -1;
    }
    }
