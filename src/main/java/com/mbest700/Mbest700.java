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
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Mbest700 implements ClientModInitializer {
    public static final MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();
    
    private static long crystalTimer, anchorTimer, shieldTimer, swordTimer, xpTimer = 0;
    private static int anchorStep = -1; // -1: Beklemede

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", "Combat").addSetting("Speed", 25.0, 1.0, 50.0));
        addMod(new Module("AutoAnchor", "Combat"));
        addMod(new Module("ShieldCracker", "Combat"));
        addMod(new Module("AutoSwordHit", "Combat").addSetting("LookTime", 0.5, 0.1, 2.0));
        addMod(new Module("Velocity", "Combat").addSetting("Reduce", 100.0, 0.0, 100.0));
        addMod(new Module("TotemCounter", "Render")); // İsim üstü totem sayacı
        addMod(new Module("SmartTotem", "Player"));
        addMod(new Module("FastXP", "Player").addSetting("Speed", 20.0, 1.0, 20.0));
        addMod(new Module("FullBright", "Render"));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (getMod("SmartTotem").enabled) doSmartTotem();
        if (getMod("FullBright").enabled) mc.options.getGamma().setValue(100.0);
        if (getMod("FastXP").enabled) doFastXP();
        
        // Velocity (Knockback İptali)
        if (getMod("Velocity").enabled && mc.player.hurtTime > 0) {
            double reduce = getMod("Velocity").getSetting("Reduce").val / 100.0;
            mc.player.setVelocity(mc.player.getVelocity().multiply(1.0 - reduce, 1.0, 1.0 - reduce));
        }

        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
        if (getMod("AutoSwordHit").enabled) doAutoSwordHit();
        
        // Gelişmiş Tek Seferlik Anchor Döngüsü
        if (anchorStep != -1) doAutoAnchorSequence();
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

    // --- TAM İSTEDİĞİN ANCHOR SIRALAMASI ---
    private static void doAutoAnchorSequence() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) { anchorStep = -1; return; }

        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);
        if (anc == -1 || glow == -1) { anchorStep = -1; return; }

        long now = System.currentTimeMillis();

        switch (anchorStep) {
            case 0: // Anchor Koy
                mc.player.getInventory().selectedSlot = anc;
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                anchorTimer = now;
                anchorStep = 1;
                break;
            case 1: // 0.2sn Bekle -> Glowstone Koy
                if (now - anchorTimer >= 200) {
                    mc.player.getInventory().selectedSlot = glow;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorTimer = now;
                    anchorStep = 2;
                }
                break;
            case 2: // 0.1sn Bekle -> Patlat (Önce Totem Kontrolü)
                if (now - anchorTimer >= 100) {
                    // Patlatmadan önce Totem slotuna geçmeye çalış (eğer varsa) yoksa Anchor ile patlat
                    int totem = findItem(Items.TOTEM_OF_UNDYING);
                    mc.player.getInventory().selectedSlot = (totem != -1) ? totem : anc;
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
                    anchorStep = -1; // Döngüyü bitir (Sürekli patlatmaz)
                }
                break;
        }
    }

    private static void doFastXP() {
        if (mc.options.useKey.isPressed() && mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            double speed = getMod("FastXP").getSetting("Speed").val;
            if (System.currentTimeMillis() - xpTimer < (1000 / speed)) return;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            xpTimer = System.currentTimeMillis();
        }
    }

    private static void doSmartTotem() {
        // Envanterde totem varsa ve offhand boşsa veya başka bir şey varsa anında çek
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (int i = 0; i < 36; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    mc.interactionManager.clickSlot(0, i < 9 ? i + 36 : i, 45, SlotActionType.SWAP, mc.player);
                    break;
                }
            }
        }
    }

    // --- RENDER: TOTEM COUNTER ---
    public static void onRenderNameTag(PlayerEntity player, DrawContext context, float tickDelta) {
        if (!getMod("TotemCounter").enabled || player == mc.player) return;
        
        int totemCount = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) totemCount++;
        }
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) totemCount++;

        String text = "§e[§6Totems: " + totemCount + "§e]";
        // Bu kısım MixinEntityRenderer üzerinden çağrılmalıdır. Basit gösterim:
        context.drawText(mc.textRenderer, text, 0, -10, 0xFFFFFF, true);
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        // V tuşuna basıldığında döngüyü sadece BAŞLATIR
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled && anchorStep == -1) {
            anchorStep = 0;
        }
    }

    // --- DİĞER METODLAR (Aynı) ---
    private static void doShieldCracker() {
        if (System.currentTimeMillis() - shieldTimer < 1000) return;
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
                    mc.player.getInventory().selectedSlot = old;
                    swordTimer = 0;
                }
            }
        } else { swordTimer = 0; }
    }

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

    // Modül ve GUI kodları buraya gelecek (v4'teki ile aynı yapı)
}
