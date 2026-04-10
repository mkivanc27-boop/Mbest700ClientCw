package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    private static long lastCrystalTime, lastAnchorTime, lastShieldTime;
    private static int anchorStep = 0;
    public static boolean isBinding = false;
    public static Module bindingModule = null;

    @Override
    public void onInitializeClient() {
        initModules();
    }

    public static void initModules() {
        // --- COMBAT ---
        Module autoCrystal = new Module("AutoCrystal", "Combat", GLFW.GLFW_KEY_C);
        autoCrystal.addSetting(new Setting("Speed", 15.0, 1.0, 40.0, true));
        addMod(autoCrystal);

        Module autoAnchor = new Module("AutoAnchor", "Combat", GLFW.GLFW_KEY_V);
        autoAnchor.addSetting(new Setting("Delay", 120.0, 10.0, 500.0, true));
        addMod(autoAnchor);

        Module shieldCracker = new Module("ShieldCracker", "Combat", 0);
        addMod(shieldCracker);

        // --- PLAYER ---
        Module fastXP = new Module("FastXP", "Player", GLFW.GLFW_KEY_Y);
        fastXP.addSetting(new Setting("Packets", 2.0, 1.0, 10.0, true));
        addMod(fastXP);

        addMod(new Module("FullBright", "Render", GLFW.GLFW_KEY_B));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;
        moduleMap.values().stream().filter(m -> m.enabled).forEach(Mbest700::runLogic);
    }

    private static void runLogic(Module m) {
        switch (m.name) {
            case "AutoCrystal": doAutoCrystal(m); break;
            case "AutoAnchor": doAutoAnchor(m); break;
            case "ShieldCracker": doShieldCracker(); break;
            case "FastXP": doFastXP(m); break;
            case "FullBright": mc.options.getGamma().setValue(100.0); break;
        }
    }

    private static void doAutoCrystal(Module m) {
        long delay = (long) (1000 / m.getSetting("Speed").val);
        if (System.currentTimeMillis() - lastCrystalTime < delay) return;

        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.distanceTo(crystal) < 5) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = System.currentTimeMillis();
            }
        });
    }

    private static void doAutoAnchor(Module m) {
        if (System.currentTimeMillis() - lastAnchorTime < m.getSetting("Delay").val) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult bHit)) return;

        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);

        if (anc != -1 && glow != -1) {
            if (anchorStep == 0) { // Koy
                inventorySelect(anc); mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); anchorStep = 1;
            } else if (anchorStep == 1) { // Doldur
                inventorySelect(glow); mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); anchorStep = 2;
            } else if (anchorStep == 2) { // Patlat
                inventorySelect(anc); mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); anchorStep = 0;
            }
            lastAnchorTime = System.currentTimeMillis();
        }
    }

    private static void doShieldCracker() {
        if (mc.targetedEntity instanceof PlayerEntity target && target.isUsingItem() && target.getOffHandStack().isOf(Items.SHIELD)) {
            int axe = -1;
            for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) axe = i;
            if (axe != -1) {
                inventorySelect(axe);
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private static void doFastXP(Module m) {
        if (mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE) && mc.options.useKey.isPressed()) {
            for (int i = 0; i < (int)m.getSetting("Packets").val; i++) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
        }
    }

    private static void inventorySelect(int slot) {
        mc.player.getInventory().selectedSlot = slot;
        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
    }

    private static int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static void onKey(int key, int action) {
        if (action != GLFW.GLFW_PRESS) return;
        if (isBinding && bindingModule != null) {
            bindingModule.key = (key == GLFW.GLFW_KEY_ESCAPE) ? 0 : key;
            isBinding = false;
            return;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new MeteorGui());
        moduleMap.values().forEach(m -> { if (m.key == key) m.toggle(); });
    }

    // --- CLASSES ---
    public static class Module {
        public String name, category;
        public int key;
        public boolean enabled = false;
        public List<Setting> settings = new ArrayList<>();
        public Module(String n, String c, int k) { name = n; category = c; key = k; }
        public void toggle() { enabled = !enabled; }
        public void addSetting(Setting s) { settings.add(s); }
        public Setting getSetting(String n) { return settings.stream().filter(s -> s.name.equals(n)).findFirst().orElse(null); }
    }

    public static class Setting {
        public String name;
        public double val, min, max;
        public boolean isInt;
        public Setting(String n, double v, double mi, double ma, boolean i) { name = n; val = v; min = mi; max = ma; isInt = i; }
    }

    public static class MeteorGui extends Screen {
        private Module selected = null;
        public MeteorGui() { super(Text.of("Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = 20;
            String[] cats = {"Combat", "Player", "Render"};
            for (String cat : cats) {
                int y = 20;
                context.fill(x, y, x + 100, y + 15, 0xFF5500AA); // Mor Başlık
                context.drawText(textRenderer, cat, x + 5, y + 4, -1, true);
                y += 17;
                for (Module m : moduleMap.values()) {
                    if (!m.category.equals(cat)) continue;
                    int col = m.enabled ? 0xFF22AA22 : 0xFF333333;
                    context.fill(x, y, x + 100, y + 14, col);
                    context.drawText(textRenderer, m.name, x + 4, y + 3, -1, true);
                    if (m == selected) {
                        int sy = y + 15;
                        for (Setting s : m.settings) {
                            context.fill(x + 2, sy, x + 98, sy + 12, 0xFF111111);
                            double prog = (s.val - s.min) / (s.max - s.min);
                            context.fill(x + 2, sy + 10, x + 2 + (int)(96 * prog), sy + 12, 0xFF5500AA);
                            context.drawText(textRenderer, s.name + ":" + (s.isInt ? (int)s.val : s.val), x + 4, sy + 2, -1, true);
                            sy += 14;
                        }
                    }
                    y += (m == selected ? 17 + (m.settings.size() * 14) : 15);
                }
                x += 110;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Tıklama mantığı (Basitleştirilmiş)
            for (Module m : moduleMap.values()) {
                // Modül seçme ve toggle mantığını buraya ekleyebilirsin
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
