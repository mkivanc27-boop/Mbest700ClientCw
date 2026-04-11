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
    
    private static long crystalTimer, anchorTimer, shieldTimer, swordTimer, xpTimer = 0;
    private static int anchorStep = -1;
    private static BlockPos targetAnchorPos = null;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        // --- TÜM MODÜLLER (Eksiksiz Liste) ---
        addMod(new Module("AutoCrystal", "Combat").addSetting("Speed", 45.0, 1.0, 100.0));
        addMod(new Module("AutoAnchor", "Combat").addSetting("Delay", 30.0, 5.0, 200.0));
        addMod(new Module("ShieldCracker", "Combat"));
        addMod(new Module("AutoSwordHit", "Combat").addSetting("LookTime", 0.5, 0.1, 2.0));
        addMod(new Module("Velocity", "Combat").addSetting("Reduce", 100.0, 0.0, 100.0));
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("FastXP", "Player").addSetting("Speed", 20.0, 1.0, 20.0));
        addMod(new Module("FullBright", "Render"));
        addMod(new Module("TotemCounter", "Render"));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        // Render & Utility
        if (getMod("FullBright").enabled) mc.options.getGamma().setValue(100.0);

        // Smart Totem (Sessiz Çekim)
        if (getMod("SmartTotem").enabled && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findTotemAnywhere();
            if (slot != -1) mc.interactionManager.clickSlot(0, slot, 45, SlotActionType.SWAP, mc.player);
        }

        // Combat İşlemleri
        if (getMod("FastXP").enabled) doFastXP();
        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        if (anchorStep != -1) doLockedAnchor();

        // Velocity (Anti-Knockback)
        if (getMod("Velocity").enabled && mc.player.hurtTime > 0) {
            double reduce = getMod("Velocity").getSetting("Reduce").val / 100.0;
            mc.player.setVelocity(mc.player.getVelocity().multiply(reduce, 1.0, reduce));
        }
    }

    // --- ÖNCEDEN KALDIRILMAMIŞ KRİTİK ÖZELLİKLER ---

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - shieldTimer < 200) return;
        mc.world.getEntities().forEach(e -> {
            if (e instanceof PlayerEntity target && target != mc.player && target.isUsingShield()) {
                int axe = findAxe();
                if (axe != -1) {
                    mc.player.getInventory().selectedSlot = axe;
                    mc.interactionManager.attackEntity(mc.player, target);
                    shieldTimer = System.currentTimeMillis();
                }
            }
        });
    }

    private static void doAutoSwordHit() {
        if (System.currentTimeMillis() - swordTimer < 500) return;
        mc.world.getEntities().forEach(e -> {
            if (e instanceof PlayerEntity target && target != mc.player && mc.player.distanceTo(target) < 3.5) {
                int sword = findItemHotbar(Items.NETHERITE_SWORD);
                if (sword != -1) {
                    mc.player.getInventory().selectedSlot = sword;
                    mc.interactionManager.attackEntity(mc.player, target);
                    swordTimer = System.currentTimeMillis();
                }
            }
        });
    }

    private static void doLockedAnchor() {
        if (targetAnchorPos == null) { anchorStep = -1; return; }
        Vec3d center = Vec3d.ofCenter(targetAnchorPos);
        float[] rots = getRotations(center);
        // Hata Fix: 4 Parametreli Paket
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround(), true));

        int anc = findItemHotbar(Items.RESPAWN_ANCHOR);
        int glow = findItemHotbar(Items.GLOWSTONE);
        if (anc == -1 || glow == -1) { anchorStep = -1; return; }

        long now = System.currentTimeMillis();
        double delay = getMod("AutoAnchor").getSetting("Delay").val;
        BlockHitResult bhr = new BlockHitResult(center, net.minecraft.util.math.Direction.UP, targetAnchorPos, false);

        switch (anchorStep) {
            case 0: mc.player.getInventory().selectedSlot = anc; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorTimer = now; anchorStep = 1; break;
            case 1: if (now - anchorTimer >= delay) { mc.player.getInventory().selectedSlot = glow; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorTimer = now; anchorStep = 2; } break;
            case 2: if (now - anchorTimer >= delay) { mc.player.getInventory().selectedSlot = anc; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorStep = -1; targetAnchorPos = null; } break;
        }
    }

    private static void doFastXP() {
        if (mc.options.useKey.isPressed() && mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            if (System.currentTimeMillis() - xpTimer >= (1000 / getMod("FastXP").getSetting("Speed").val)) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                xpTimer = System.currentTimeMillis();
            }
        }
    }

    private static void doAutoCrystal() {
        if (System.currentTimeMillis() - crystalTimer < (1000 / getMod("AutoCrystal").getSetting("Speed").val)) return;
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

    // --- GELİŞMİŞ MENÜ (SAĞ TIK ÖZELLİKLİ) ---
    public static class AmethystGui extends Screen {
        private String selectedMod = "";
        private float fade = 0;
        public AmethystGui() { super(Text.literal("Amethyst")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            fade = Math.min(fade + 0.1f, 1.0f);
            context.fill(10, 10, 210, 280, (int)(0xDD * fade) << 24 | 0x050505);
            context.drawText(this.textRenderer, "§dAmethyst ULTIMATE §fV11", 20, 20, 0xFFFFFF, true);
            int x = 20; int y = 40;
            for (Module m : moduleMap.values()) {
                int baseCol = m.enabled ? 0xFF9933FF : 0xFF444444;
                context.fill(x, y, x + 4, y + 12, baseCol);
                context.drawText(this.textRenderer, m.name, x + 10, y + 2, 0xFFFFFF, false);
                if (m.name.equals(selectedMod)) {
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        context.drawText(this.textRenderer, " §b> " + s.name + ": §f" + String.format("%.1f", s.val), x + 15, y, 0xBBBBBB, false);
                    }
                }
                y += 18;
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 40;
            for (Module m : moduleMap.values()) {
                if (mouseX > 20 && mouseX < 190 && mouseY > y && mouseY < y + 15) {
                    if (button == 0) m.toggle();
                    else if (button == 1) selectedMod = selectedMod.equals(m.name) ? "" : m.name;
                    return true;
                }
                if (m.name.equals(selectedMod)) {
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        if (mouseY > y && mouseY < y + 12) {
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

    // --- YARDIMCI ARAÇLAR ---
    private static int findTotemAnywhere() {
        for (int i = 0; i < 45; i++) if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i < 9 ? i + 36 : i;
        return -1;
    }
    private static int findItemHotbar(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    private static int findAxe() {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) return i;
        return -1;
    }
    private static float[] getRotations(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double diffXZ = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        return new float[]{(float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90F, (float) -Math.toDegrees(Math.atan2(diff.y, diffXZ))};
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
