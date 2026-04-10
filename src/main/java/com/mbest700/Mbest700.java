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
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
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

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        // --- COMBAT ---
        addMod(new Module("AutoCrystal", "Combat", GLFW.GLFW_KEY_C, 16.0)); 
        addMod(new Module("AutoAnchor", "Combat", GLFW.GLFW_KEY_V, 50.0)); // Delay (ms)
        addMod(new Module("ShieldCracker", "Combat", 0, 1.0));
        addMod(new Module("Reach", "Combat", 0, 4.2));
        addMod(new Module("Velocity", "Combat", 0, 0.0));

        // --- PLAYER / UTILS ---
        addMod(new Module("SmartTotem", "Player", 0, 1.0));
        addMod(new Module("AutoEat", "Player", 0, 14.0));
        addMod(new Module("FastXP", "Player", GLFW.GLFW_KEY_Y, 20.0)); // Atış hızı

        // --- RENDER (BASE HUNTER) ---
        addMod(new Module("StorageESP", "Render", 0, 64.0));
        addMod(new Module("FullBright", "Render", GLFW.GLFW_KEY_B, 1.0));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;
        moduleMap.values().stream().filter(m -> m.enabled).forEach(m -> {
            if (m.name.equals("SmartTotem")) doSmartTotem();
            if (m.name.equals("AutoCrystal")) doAutoCrystal(m);
            if (m.name.equals("Velocity") && mc.player.hurtTime > 0) mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            if (m.name.equals("AutoEat")) doAutoEat(m);
            if (m.name.equals("ShieldCracker")) doShieldCracker();
            if (m.name.equals("FullBright")) mc.options.getGamma().setValue(100.0);
        });
    }

    private static void doAutoCrystal(Module m) {
        long delay = (long) (1000 / m.val);
        if (System.currentTimeMillis() - lastCrystalTime < delay) return;
        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.canSee(e) && mc.player.distanceTo(crystal) < getMod("Reach").val) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = System.currentTimeMillis();
            }
        });
    }

    private static void doAutoAnchor() {
        Module m = getMod("AutoAnchor");
        if (System.currentTimeMillis() - lastAnchorTime < m.val) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) return;
        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);

        if (anc != -1 && glow != -1) {
            int oldSlot = mc.player.getInventory().selectedSlot;
            // Sırayla paket gönder ve bekleme süresini güncelle
            mc.player.getInventory().selectedSlot = anc;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            
            mc.player.getInventory().selectedSlot = glow;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); // Doldur
            
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); // Patlat
            
            mc.player.getInventory().selectedSlot = oldSlot; // Eski eşyaya dön
            lastAnchorTime = System.currentTimeMillis();
        }
    }

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - lastShieldTime < 500) return; // Spam engelleme
        if (mc.targetedEntity instanceof PlayerEntity target && target.isHolding(Items.SHIELD)) {
            int axe = findAxe();
            if (axe != -1) {
                int oldSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = axe;
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.getInventory().selectedSlot = oldSlot; // Kırdıktan sonra dön
                lastShieldTime = System.currentTimeMillis();
            }
        }
    }

    private static void doAutoEat(Module m) {
        if (mc.player.getHungerManager().getFoodLevel() <= m.val) {
            int food = findFood();
            if (food != -1) {
                mc.player.getInventory().selectedSlot = food;
                mc.options.useKey.setPressed(true);
            }
        } else if (mc.options.useKey.isPressed()) {
            mc.options.useKey.setPressed(false);
        }
    }

    // --- YARDIMCI METOTLAR ---
    public static Module getMod(String name) { return moduleMap.get(name); }
    private static int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static int findAxe() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        return -1;
    }
    private static int findFood() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getComponents().contains(DataComponentTypes.FOOD)) return i;
        return -1;
    }
    private static void doSmartTotem() {
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = -1;
            for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) { slot = i; break; }
            if (slot != -1) mc.interactionManager.clickSlot(0, slot < 9 ? slot + 36 : slot, 45, SlotActionType.SWAP, mc.player);
        }
    }
    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new MeteorGui());
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled) doAutoAnchor();
    }

    // --- MODÜL YAPISI ---
    public static class Module {
        public String name, category;
        public int key;
        public boolean enabled = false;
        public double val;
        public Module(String name, String category, int key, double val) {
            this.name = name; this.category = category; this.key = key; this.val = val;
        }
        public void toggle() { this.enabled = !this.enabled; }
    }

    // --- METEOR STYLE CLICK GUI ---
    public static class MeteorGui extends Screen {
        public MeteorGui() { super(Text.literal("Mbest700 Meteor")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = 40;
            String[] cats = {"Combat", "Player", "Render"};
            for (String cat : cats) {
                context.fill(x, 30, x + 90, 45, 0xFF444444); // Başlık arka plan
                context.drawText(this.textRenderer, "§l" + cat, x + 5, 34, 0xFFFFFF, false);
                int y = 47;
                for (Module m : moduleMap.values()) {
                    if (m.category.equals(cat)) {
                        int bgColor = m.enabled ? 0xFF3366FF : 0xFF222222; // Meteor mavisi / Siyah
                        context.fill(x, y, x + 90, y + 14, bgColor);
                        context.drawText(this.textRenderer, m.name, x + 4, y + 3, 0xFFFFFF, false);
                        if (m.val > 0) context.drawText(this.textRenderer, "§7" + m.val, x + 70, y + 3, 0xAAAAAA, false);
                        y += 15;
                    }
                }
                x += 100;
            }
            context.drawText(this.textRenderer, "§7[Sol Tık: Aç/Kapat] [Sağ Tık: Ayar Artır] [V: AutoAnchor]", 40, 220, 0xAAAAAA, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = 40;
            String[] cats = {"Combat", "Player", "Render"};
            for (String cat : cats) {
                int y = 47;
                for (Module m : moduleMap.values()) {
                    if (m.category.equals(cat)) {
                        if (mouseX > x && mouseX < x + 90 && mouseY > y && mouseY < y + 14) {
                            if (button == 0) m.toggle();
                            if (button == 1) { // Özelleştirme (Sağ Tık)
                                if (m.name.equals("Reach")) m.val = (m.val >= 6) ? 3 : m.val + 0.1;
                                else m.val = (m.val >= 100) ? 1 : m.val + 5;
                            }
                            return true;
                        }
                        y += 15;
                    }
                }
                x += 100;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
