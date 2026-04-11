package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
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
    
    private static long anchorTimer, shieldTimer, swordTimer, xpTimer, crystalTimer, totemTimer = 0;
    private static int anchorStep = -1;
    private static BlockPos targetAnchorPos = null;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", "Sag tikla seri kristal koyar ve patlatir.")
            .addSetting("Speed", 20.0, 1.0, 50.0));
        addMod(new Module("AutoAnchor", "V tusuyla otomatik Anchor patlatir (Totemle).")
            .addSetting("Delay", 30.0, 5.0, 200.0));
        addMod(new Module("Velocity", "Aldiginiz geri tepmeyi yuzdesel azaltir.")
            .addSetting("Reduce", 100.0, 0.0, 100.0));
        addMod(new Module("TriggerBot", "Baktiginiz oyuncuya otomatik vurur.")
            .addSetting("Range", 3.0, 2.0, 6.0)); 
        addMod(new Module("ShieldCracker", "Kalkan kullananlarin kalkanini dusurur."));
        addMod(new Module("AutoSwordHit", "Kilica gecer, vurur ve ESKI ITEMINE doner.")
            .addSetting("Range", 3.8, 2.0, 6.0));
        addMod(new Module("SmartTotem", "Envanteri acip totem alir ve kapatir.")
            .addSetting("SwapDelay", 50.0, 0.0, 500.0)); 
        addMod(new Module("FastXP", "XP sisesini cok seri firlatir.")
            .addSetting("Speed", 20.0, 1.0, 20.0));
        addMod(new Module("NightVision", "Karanligi aydinlatir.")); 
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (getMod("SmartTotem").enabled && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            long delay = (long) getMod("SmartTotem").getSetting("SwapDelay").val;
            if (System.currentTimeMillis() - totemTimer >= delay) {
                int slot = findTotemAnywhere();
                if (slot != -1) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slot, 45, SlotActionType.SWAP, mc.player);
                    mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
                    totemTimer = System.currentTimeMillis();
                }
            }
        }

        if (getMod("NightVision").enabled) {
            mc.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 1000, 0, false, false));
        }

        if (getMod("Velocity").enabled && mc.player.hurtTime > 0) {
            double red = getMod("Velocity").getSetting("Reduce").val / 100.0;
            mc.player.setVelocity(mc.player.getVelocity().multiply(red, 1.0, red));
        }

        if (getMod("FastXP").enabled) doFastXP();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        if (getMod("TriggerBot").enabled) doTriggerBot();
        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (anchorStep != -1) doLockedAnchor();
    }

    private static void doTriggerBot() {
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity target) {
            double range = getMod("TriggerBot").getSetting("Range").val;
            if (target != mc.player && target.isAlive() && mc.player.distanceTo(target) <= range) {
                if (mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private static void doLockedAnchor() {
        if (targetAnchorPos == null) { anchorStep = -1; return; }
        Vec3d center = Vec3d.ofCenter(targetAnchorPos);
        float[] rots = getRotations(center);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(rots[0], rots[1], mc.player.isOnGround(), true));
        int anc = findItemHotbar(Items.RESPAWN_ANCHOR);
        int glow = findItemHotbar(Items.GLOWSTONE);
        int totem = findItemHotbar(Items.TOTEM_OF_UNDYING);
        if (anc == -1 || glow == -1) { anchorStep = -1; return; }
        long now = System.currentTimeMillis();
        double delay = getMod("AutoAnchor").getSetting("Delay").val;
        BlockHitResult bhr = new BlockHitResult(center, net.minecraft.util.math.Direction.UP, targetAnchorPos, false);
        switch (anchorStep) {
            case 0: mc.player.getInventory().selectedSlot = anc; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorTimer = now; anchorStep = 1; break;
            case 1: if (now - anchorTimer >= delay) { mc.player.getInventory().selectedSlot = glow; mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr); anchorTimer = now; anchorStep = 2; } break;
            case 2: if (now - anchorTimer >= delay) {
                    if (totem != -1) mc.player.getInventory().selectedSlot = totem;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                    anchorStep = -1; targetAnchorPos = null;
                } break;
        }
    }

    private static void doAutoCrystal() {
        if (!mc.options.useKey.isPressed()) return; 
        double speed = getMod("AutoCrystal").getSetting("Speed").val;
        if (System.currentTimeMillis() - crystalTimer < (1000 / speed)) return;
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystalEntity crystal) {
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
            crystalTimer = System.currentTimeMillis();
        } else if (mc.crosshairTarget instanceof BlockHitResult bhr) {
            int crySlot = findItemHotbar(Items.END_CRYSTAL);
            if (crySlot != -1) {
                int oldSlot = mc.player.getInventory().selectedSlot;
                mc.player.getInventory().selectedSlot = crySlot;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
                mc.player.getInventory().selectedSlot = oldSlot;
                crystalTimer = System.currentTimeMillis();
            }
        }
    }

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - shieldTimer < 250) return;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity target && target != mc.player && target.isBlocking()) {
                int axe = findAxe();
                if (axe != -1 && mc.player.distanceTo(target) < 4.0) {
                    mc.player.getInventory().selectedSlot = axe;
                    mc.interactionManager.attackEntity(mc.player, target);
                    shieldTimer = System.currentTimeMillis();
                }
            }
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

    private static void doAutoSwordHit() {
        if (System.currentTimeMillis() - swordTimer < 600) return;
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity target) {
            if (mc.player.distanceTo(target) < getMod("AutoSwordHit").getSetting("Range").val) {
                int sword = findItemHotbar(Items.NETHERITE_SWORD);
                if (sword == -1) sword = findItemHotbar(Items.DIAMOND_SWORD);
                if (sword != -1) {
                    int oldSlot = mc.player.getInventory().selectedSlot;
                    mc.player.getInventory().selectedSlot = sword;
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    new Timer().schedule(new TimerTask() { @Override public void run() { mc.player.getInventory().selectedSlot = oldSlot; }}, 50);
                    swordTimer = System.currentTimeMillis();
                }
            }
        }
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        if (key == GLFW.GLFW_KEY_V && anchorStep == -1) {
            if (getMod("AutoAnchor").enabled && mc.crosshairTarget instanceof BlockHitResult bhr) {
                targetAnchorPos = bhr.getBlockPos();
                anchorStep = 0;
            }
        }
    }

    public static class AmethystGui extends Screen {
        private String selectedMod = "";
        public AmethystGui() { super(Text.literal("Amethyst")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(10, 10, 245, 310, 0xEE050505);
            context.drawText(this.textRenderer, "§dMbest700 §fV28", 20, 20, 0xFFFFFF, true);
            int y = 45;
            for (Module m : moduleMap.values()) {
                context.fill(20, y, 24, y + 10, m.enabled ? 0xFF9933FF : 0xFF444444);
                context.drawText(this.textRenderer, (m.name.equals(selectedMod) ? "§e> " : "§f") + m.name, 30, y + 1, 0xFFFFFF, false);
                if (m.name.equals(selectedMod)) {
                    y += 15;
                    context.drawText(this.textRenderer, "§7" + m.info, 40, y, 0xAAAAAA, false);
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        context.drawText(this.textRenderer, "  " + s.name + ": §b" + String.format("%.1f", s.val), 40, y, 0xCCCCCC, false);
                    }
                }
                y += 18;
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 45;
            for (Module m : moduleMap.values()) {
                if (mouseX > 20 && mouseX < 200 && mouseY > y && mouseY < y + 15) {
                    if (button == 0) m.toggle();
                    else selectedMod = m.name;
                    return true;
                }
                if (m.name.equals(selectedMod)) {
                    y += 15;
                    for (Setting s : m.settings.values()) {
                        y += 15;
                        if (mouseX > 40 && mouseX < 180 && mouseY > y && mouseY < y + 12) {
                            if (button == 0) s.val = Math.min(s.max, s.val + 1);
                            else s.val = Math.max(s.min, s.val - 1);
                            return true;
                        }
                    }
                }
                y += 18;
            }
            return false;
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
        public String name, info; public boolean enabled = false;
        public Map<String, Setting> settings = new LinkedHashMap<>();
        public Module(String n, String i) { name = n; info = i; }
        public Module addSetting(String n, double v, double min, double max) { settings.put(n, new Setting(n, v, min, max)); return this; }
        public Setting getSetting(String n) { return settings.get(n); }
        public void toggle() { enabled = !enabled; }
    }
    public static class Setting {
        public String name; public double val, min, max;
        public Setting(String n, double v, double min, double max) { name = n; val = v; this.min = min; this.max = max; }
    }
    }
                                                          
