package com.mbest700;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Mbest700 Client - Core Module
 * Tüm hile mantığı ve ayarlar burada birleştirilmiştir.
 */
public class Mbest700 {
    public static final String MOD_ID = "mbest700";
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    // --- ÖZELLEŞTİRİLEBİLİR AYARLAR (METEOR STYLE) ---
    public static class Settings {
        public static boolean autoAnchorEnabled = false;
        public static int anchorDelay = 3; // Tick bazlı (3-5 idealdir)
        
        public static boolean shieldCrackerEnabled = false;
        public static boolean autoSwapBack = true;
        
        public static float reachDistance = 4.5f;
        
        // Ekstra Modüller
        public static boolean autoClicker = false;
        public static boolean noVelocity = false;
    }

    // --- MIXIN BAĞLANTILARI ---

    // MinecraftClientMixin ve PlayerEntityMixin tarafından çağrılır
    public static void onTick() {
        if (mc.player == null || mc.world == null) return;
        
        runAutoAnchor();
        runShieldCracker();
    }

    // KeyboardMixin'deki "cannot find symbol" hatasını çözen metod
    public static void onKey(int key, int action) {
        // action 1 = Basıldı, 0 = Bırakıldı, 2 = Basılı tutuluyor
        if (action == 1) {
            // Örnek: Sağ Shift (344) ile menü açma tetiklenebilir
            if (key == 344) {
                // Menü açma kodu buraya
            }
        }
    }

    // --- MODÜL MANTIKLARI ---

    private static int anchorTimer = 0;
    private static void runAutoAnchor() {
        if (!Settings.autoAnchorEnabled) return;

        if (anchorTimer > 0) {
            anchorTimer--;
            return;
        }

        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            // El kontrolü ve işlem
            if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                anchorTimer = Settings.anchorDelay;
            } else if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                anchorTimer = Settings.anchorDelay;
            }
        }
    }

    private static void runShieldCracker() {
        if (!Settings.shieldCrackerEnabled) return;

        if (mc.targetedEntity instanceof net.minecraft.entity.LivingEntity target) {
            // Rakip kalkanı aktif kullanıyorsa saldır
            if (target.isUsingItem() && target.getActiveItem().isOf(Items.SHIELD)) {
                if (mc.player.getMainHandStack().getItem() instanceof net.minecraft.item.AxeItem) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            } else if (Settings.autoSwapBack) {
                // Kalkan kalktığında kılıca (0. slot) dönme mantığı eklenebilir
            }
        }
    }

    // --- GUI KONTROL SİSTEMİ ---
    public static class MeteorGui {
        public static void toggle(String moduleName) {
            switch (moduleName.toLowerCase()) {
                case "autoanchor" -> Settings.autoAnchorEnabled = !Settings.autoAnchorEnabled;
                case "shieldcracker" -> Settings.shieldCrackerEnabled = !Settings.shieldCrackerEnabled;
            }
        }
    }
}
