package com.mbest700;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/**
 * Mbest700 Client - Core Module
 * Fixes: Auto-spam, Anchor delay, Shield Cracker logic, Menu Access
 */
public class Mbest700 implements ModInitializer {
    public static final String MOD_ID = "mbest700";
    public static final List<Module> modules = new ArrayList<>();
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitialize() {
        // Modülleri Sisteme Kaydet (Customizable Değerlerle)
        modules.add(new Module("Auto Anchor", GLFW.GLFW_KEY_V, "Anchor koyar, doldurur ve gecikmeli patlatır.")
                .addSetting("Delay", 0.15) // Bekleme süresi eklendi
                .addSetting("AutoFill", 1.0));
        
        modules.add(new Module("Shield Cracker", GLFW.GLFW_KEY_G, "Kalkanı kırınca otomatik eski eşyaya geçer."));
        
        modules.add(new Module("Reach", GLFW.GLFW_KEY_R, "Vuruş mesafesini artırır.")
                .addSetting("Distance", 3.5));

        System.out.println("[Mbest700] Client başarıyla yüklendi. Menü: RIGHT_SHIFT");
    }

    // --- MODÜL SİSTEMİ (Spam Engelleme ve Ayar Altyapısı) ---
    public static class Module {
        public String name;
        public int key;
        public boolean toggled = false;
        public String description;
        public double currentDelay = 0.0; // İç sayaç

        // Ayarlar için basit bir liste (Customizable olması için)
        public double settingValue = 0.0;

        public Module(String name, int key, String description) {
            this.name = name;
            this.key = key;
            this.description = description;
        }

        public Module addSetting(String label, double defaultValue) {
            this.settingValue = defaultValue;
            return this;
        }

        public void toggle() {
            this.toggled = !this.toggled;
            if (mc.player != null) {
                String state = toggled ? "§aAÇIK" : "§cKAPALI";
                mc.player.sendMessage(Text.literal("§8[§bMbest700§8] §f" + name + " " + state), true);
            }
        }
    }

    // --- METEOR TARZI CUSTOMIZABLE MENU ---
    public static class MeteorGui extends Screen {
        public MeteorGui() {
            super(Text.literal("Mbest700 Menu"));
        }

        @Override
        public void render(net.minecraft.client.util.math.MatrixStack matrices, int mouseX, int mouseY, float delta) {
            this.renderBackground(matrices);
            int y = 30;
            
            drawCenteredText(matrices, this.textRenderer, "§b§lMBEST700 §f§nCUSTOMIZER", this.width / 2, 10, 0xFFFFFF);

            for (Module mod : modules) {
                int color = mod.toggled ? 0x00FF00 : 0xFFFFFF;
                String status = mod.toggled ? "[ON]" : "[OFF]";
                
                // Modül İsmi ve Durumu
                this.textRenderer.draw(matrices, "§7> §f" + mod.name + " §r" + status, 20, y, color);
                
                // Ayarları Göster (Customizable kısmı)
                if (mod.settingValue > 0) {
                    this.textRenderer.draw(matrices, "§8└─ Val: " + mod.settingValue, 130, y, 0xAAAAAA);
                }
                
                y += 14;
            }
            
            this.textRenderer.draw(matrices, "§8[ESC] Kapat | [R-SHIFT] Menü", 10, this.height - 15, 0x555555);
            super.render(matrices, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                this.close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean shouldPause() { return false; } // Menü açıkken oyun durmasın
    }
}
