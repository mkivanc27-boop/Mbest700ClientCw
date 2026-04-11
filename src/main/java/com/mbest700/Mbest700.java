package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    // Değişken Tanımlamaları (Build Hataları Fix)
    private static long anchorTimer, shieldTimer, swordTimer, xpTimer, maceTimer = 0;
    private static int anchorStep = -1;
    private static int lastSwordSlot = -1;
    private static BlockPos targetAnchorPos = null;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AirPlace Anchor", "Combat").addSetting("Delay", 30.0, 5.0, 200.0));
        addMod(new Module("AutoAnchor", "Combat").addSetting("Delay", 30.0, 5.0, 200.0));
        addMod(new Module("AutoMace", "Combat").addSetting("FallDist", 1.5, 0.5, 10.0));
        addMod(new Module("ShieldCracker", "Combat"));
        addMod(new Module("AutoSwordHit", "Combat").addSetting("Range", 3.8, 2.0, 6.0));
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("FastXP", "Player").addSetting("Speed", 20.0, 1.0, 20.0));
        addMod(new Module("NightVision", "Render")); // FullBright yerine NightVision [yeni]
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        // SmartTotem
        if (getMod("SmartTotem").enabled && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findTotemAnywhere();
            if (slot != -1) mc.interactionManager.clickSlot(0, slot, 45, SlotActionType.SWAP, mc.player);
        }

        // NightVision Fix
        if (getMod("NightVision").enabled) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 0, false, false));
        }

        if (getMod("FastXP").enabled) doFastXP();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        if (getMod("AutoMace").enabled) doAutoMace();
        if (anchorStep != -1) doLockedAnchor();
    }

    // --- AUTO MACE FIX ---
    private static void doAutoMace() {
        if (mc.player.fallDistance > getMod("AutoMace").getSetting("FallDist").val) {
            if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity target) {
                int mace = findItemHotbar(Items.MACE);
                if (mace != -1 && mc.player.distanceTo(target) < 4.5) {
                    int prev = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = mace;
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    // Vurduktan sonra geri dön
                    new Timer().schedule(new TimerTask() { @Override public void run() { mc.player.getInventory().selectedSlot = prev; }}, 50);
                }
            }
        }
    }

    // --- AUTO SWORD HIT: BAKINCA VUR VE GERİ DÖN ---
    private static void doAutoSwordHit() {
        if (System.currentTimeMillis() - swordTimer < 600) return;
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity target) {
            if (mc.player.distanceTo(target) < getMod("AutoSwordHit").getSetting("Range").val) {
                int sword = findItemHotbar(Items.NETHERITE_SWORD);
                if (sword != -1) {
                    lastSwordSlot = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = sword;
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    swordTimer = System.currentTimeMillis();
                    // Eski slota dönüş
                    new Timer().schedule(new TimerTask() { @Override public void run() { mc.player.getInventory().selectedSlot = lastSwordSlot; }}, 50);
                }
            }
        }
    }

    // --- ANCHOR SİSTEMLERİ ---
    private static void doLockedAnchor() {
        if (targetAnchorPos == null) { anchorStep = -1; return; }
        Vec3d center = Vec3d.ofCenter(targetAnchorPos);
        float[] rots = getRotations(center);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround(), true));

        int anc = findItemHotbar(Items.RESPAWN_ANCHOR);
        int glow = findItemHotbar(Items.GLOWSTONE);
        int hTotem = findItemHotbar(Items.TOTEM_OF_UNDYING);

        if (anc == -1 || glow == -1) { anchorStep = -1; return; }
        long now = System.currentTimeMillis();
        double delay = getMod("AutoAnchor").enabled ? getMod("AutoAnchor").getSetting("Delay").val : getMod("AirPlace Anchor").getSetting("Delay").val;
        
        // Yere ve Havaya Etki: Direction seçimi
        BlockHitResult bhr = new BlockHitResult(center, net.minecraft.util.math.Direction.UP, targetAnchorPos, false);

        switch (anchorStep) {
            case 0: mc.player.getInventory().selectedSlot = anc; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorTimer = now; anchorStep = 1; break;
            case 1: if (now - anchorTimer >= delay) { mc.player.getInventory().selectedSlot = glow; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorTimer = now; anchorStep = 2; } break;
            case 2: if (now - anchorTimer >= delay) {
                mc.player.getInventory().selectedSlot = (hTotem != -1) ? hTotem : anc;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                anchorStep = -1; targetAnchorPos = null;
            } break;
        }
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        if (key == GLFW.GLFW_KEY_V && anchorStep == -1) {
            if (getMod("AutoAnchor").enabled || getMod("AirPlace Anchor").enabled) {
                if (mc.crosshairTarget instanceof BlockHitResult bhr) {
                    targetAnchorPos = bhr.getBlockPos();
                    anchorStep = 0;
                }
            }
        }
    }

    // GUI ve Yardımcılar (Değişmedi ama V15 etiketi eklendi)
    public static class AmethystGui extends Screen {
        public AmethystGui() { super(Text.literal("Amethyst")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(10, 10, 220, 290, 0xDD050505);
            context.drawText(this.textRenderer, "§dMbest700 §fV15", 20, 20, 0xFFFFFF, true);
            int x = 20; int y = 40;
            for (Module m : moduleMap.values()) {
                context.fill(x, y, x + 4, y + 12, m.enabled ? 0xFF9933FF : 0xFF444444);
                context.drawText(this.textRenderer, m.name, x + 10, y + 2, 0xFFFFFF, false);
                y += 18;
            }
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
                             
