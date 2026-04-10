package com.mbest700;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.ArrayList;
import java.util.List;

public class Mbest700 {
    public static final String MOD_ID = "mbest700";
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    // MODÜL AYARLARI (Customizable)
    public static class Settings {
        // AutoAnchor
        public static boolean autoAnchorEnabled = false;
        public static int anchorDelay = 2; // Tick bazlı bekleme süresi
        
        // ShieldCracker
        public static boolean shieldCrackerEnabled = false;
        public static boolean autoSwapBack = true; // Kalkan kırınca eski eşyaya geç
        
        // Reach
        public static float reachDistance = 4.5f;

        // Yeni Hileler
        public static boolean autoClicker = false;
        public static boolean noVelocity = false;
    }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        runAutoAnchor();
        runShieldCracker();
        runReachHack();
    }

    // --- FIX: AUTO ANCHOR (BEKLEME SÜRELİ) ---
    private static int anchorTimer = 0;
    private static void runAutoAnchor() {
        if (!Settings.autoAnchorEnabled) return;

        if (anchorTimer > 0) {
            anchorTimer--;
            return;
        }

        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            
            // Eğer elimizde Anchor varsa koy, Glowstone varsa doldur ve patlat
            if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                anchorTimer = Settings.anchorDelay; // Bekleme süresi eklendi
            } else if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                anchorTimer = Settings.anchorDelay;
            }
        }
    }

    // --- FIX: SHIELD CRACKER (AKILLI DURDURMA) ---
    private static void runShieldCracker() {
        if (!Settings.shieldCrackerEnabled) return;

        if (mc.targetedEntity instanceof net.minecraft.entity.LivingEntity target) {
            // Sadece rakip kalkan kullanıyorsa çalış
            if (target.isUsingItem() && target.getActiveItem().isOf(Items.SHIELD)) {
                if (mc.player.getMainHandStack().isOf(Items.NETHERITE_AXE) || mc.player.getMainHandStack().isOf(Items.DIAMOND_AXE)) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else if (Settings.autoSwapBack) {
                // Kalkan kırıldıysa kılıca veya önceki slota geç (Örnek: 1. slot)
                mc.player.getInventory().selectedSlot = 0; 
            }
        }
    }

    // --- REACH HACK ---
    private static void runReachHack() {
        // Bu kısım MixinPlayerEntity içinde 'Settings.reachDistance' değişkenini kullanacak şekilde ayarlanmıştır.
    }

    // --- METEOR STYLE GUI KONTROLÜ ---
    public static class MeteorGui {
        public static void toggleModule(String name) {
            switch (name.toLowerCase()) {
                case "autoanchor" -> Settings.autoAnchorEnabled = !Settings.autoAnchorEnabled;
                case "shieldcracker" -> Settings.shieldCrackerEnabled = !Settings.shieldCrackerEnabled;
                case "reach" -> Settings.reachDistance = (Settings.reachDistance == 4.5f) ? 3.0f : 4.5f;
            }
        }
    }
}
