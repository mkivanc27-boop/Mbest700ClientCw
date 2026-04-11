package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    private static long crystalTimer, anchorTimer = 0;
    private static int anchorStep = -1;
    private static BlockPos targetAnchorPos = null;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", "Combat").addSetting("Speed", 40.0, 1.0, 100.0));
        addMod(new Module("AutoAnchor", "Combat").addSetting("Delay", 35.0, 5.0, 200.0));
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("TotemCounter", "Render"));
        addMod(new Module("Velocity", "Combat").addSetting("Reduce", 100.0, 0.0, 100.0));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        // --- SESSİZ AUTO TOTEM (Envanter Açmadan) ---
        if (getMod("SmartTotem").enabled && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findTotemAnywhere();
            if (slot != -1) {
                mc.interactionManager.clickSlot(0, slot, 45, SlotActionType.SWAP, mc.player);
            }
        }

        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (anchorStep != -1) doLockedAnchor();
    }

    // --- ANCHOR KİLİDİ & PAKET DÜZELTMESİ ---
    private static void doLockedAnchor() {
        if (targetAnchorPos == null) { anchorStep = -1; return; }

        Vec3d center = Vec3d.ofCenter(targetAnchorPos);
        float[] rots = getRotations(center);
        // HATA FİX: 4 parametre eklendi (yaw, pitch, onGround, horizontal)
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround(), true));

        int anc = findItemHotbar(Items.RESPAWN_ANCHOR);
        int glow = findItemHotbar(Items.GLOWSTONE);
        if (anc == -1 || glow == -1) { anchorStep = -1; return; }

        long now = System.currentTimeMillis();
        double delay = getMod("AutoAnchor").getSetting("Delay").val;
        BlockHitResult bhr = new BlockHitResult(center, net.minecraft.util.math.Direction.UP, targetAnchorPos, false);

        switch (anchorStep) {
            case 0:
                mc.player.getInventory().selectedSlot = anc;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                anchorTimer = now; anchorStep = 1; break;
            case 1:
                if (now - anchorTimer >= delay) {
                    mc.player.getInventory().selectedSlot = glow;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    anchorTimer = now; anchorStep = 2;
                } break;
            case 2:
                if (now - anchorTimer >= delay) {
                    mc.player.getInventory().selectedSlot = anc;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    anchorStep = -1; targetAnchorPos = null;
                } break;
        }
    }

    private static void doAutoCrystal() {
        double speed = getMod("AutoCrystal").getSetting("Speed").val;
        if (System.currentTimeMillis() - crystalTimer < (1000 / speed)) return;

        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.distanceTo(crystal) < 5.0) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                crystalTimer = System.currentTimeMillis();
            }
        });
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        if (key == GLFW.GLFW_KEY_V && anchorStep == -1) {
            if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                targetAnchorPos = bhr.getBlockPos();
                anchorStep = 0;
            }
        }
    }

    // --- ANİMASYONLU & HATASIZ MENÜ ---
    public static class AmethystGui extends Screen {
        private String selectedMod = "";
        private float fade = 0;

        public AmethystGui() { super(Text.literal("Amethyst")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            fade = Math.min(fade + 0.1f, 1.0f);
            context.fill(10, 10, 200, 280, (int)(0xDD * fade) << 24 | 0x0A0A0A);
            context.drawText(this.textRenderer, "§dAmethyst §fV8", 20, 20, 0xFFFFFF, true);

            int x = 20; int y = 40;
            for (Module m : moduleMap.values()) {
                boolean hover = mouseX > x && mouseX < x + 160 && mouseY > y && mouseY < y + 14;
                int baseCol = m.enabled ? 0xFF8800FF : 0xFF555555;
                if (hover) baseCol = 0xFFAA55FF;

                context.fill(x, y, x + 4, y + 12, baseCol);
                context.drawText(this.textRenderer, m.name, x + 10, y + 2, 0xFFFFFF, false);

                if (m.name.equals(selectedMod)) {
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        context.drawText(this.textRenderer, " §b> " + s.name + ": §f" + String.format("%.1f", s.val), x + 15, y, 0xFFFFFF, false);
                    }
                }
                y += 18;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 40;
            for (Module m : moduleMap.values()) {
                if (mouseX > 20 && mouseX < 180 && mouseY > y && mouseY < y + 15) {
                    if (button == 0) m.toggle();
                    else if (button == 1) selectedMod = selectedMod.equals(m.name) ? "" : m.name;
                    return true;
                }
                if (m.name.equals(selectedMod)) {
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        if (mouseY > y && mouseY < y + 12) {
                            // Değer değiştirme fixlendi
                            s.val = (s.val >= s.max) ? s.min : s.val + (s.max - s.min) / 5.0;
                            return true;
                        }
                    }
                }
                y += 18;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    private static int findTotemAnywhere() {
        for (int i = 0; i < 45; i++) if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i < 9 ? i + 36 : i;
        return -1;
    }
    private static int findItemHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static float[] getRotations(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ));
        return new float[]{yaw, pitch};
    }
    public static Module getMod(String name) { return moduleMap.get(name); }

    public static class Module {
        public String name; public boolean enabled = false;
        public Map<String, Setting> settings = new LinkedHashMap<>();
        public Module(String n, String c) { name = n; }
        public Module addSetting(String n, double v, double min, double max) { settings.put(n, new Setting(n, v, min, max)); return this; }
        public Setting getSetting(String n) { return settings.get(n); }
        public void toggle() { enabled = !enabled; }
    }
    public static class Setting {
        public String name; public double val, min, max;
        public Setting(String n, double v, double min, double max) { name = n; val = v; this.min = min; this.max = max; }
    }
}
