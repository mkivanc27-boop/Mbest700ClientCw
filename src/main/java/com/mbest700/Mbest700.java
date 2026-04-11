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
    private static long lastCrystalTime, lastAnchorTime, lastShieldTime, lastXpTime = 0;

    // --- AMETHYST RENK PALETİ ---
    public static final int COLOR_BG = 0xEE0A0A0A;      // Çok koyu siyah-gri
    public static final int COLOR_AMETHYST = 0xFFA020F0; // Canlı Mor
    public static final int COLOR_AMETHYST_DARK = 0xBB6A1B9A; // Koyu Mor (Kategori)
    public static final int COLOR_TEXT = 0xFFFFFFFF;    // Beyaz
    public static final int COLOR_TEXT_DIM = 0xFFAAAAAA;// Gri

    @Override
    public void onInitializeClient() {
        init();
        System.out.println("§l§dMbest700§7: §fRight Shift ile §5Amethyst §fMenüsünü Aç!");
    }

    public static void init() {
        // --- COMBAT ---
        addMod(new Module("AutoCrystal", "Combat", GLFW.GLFW_KEY_C)
            .addSetting("PlaceRange", 5.0, 3.0, 7.0)
            .addSetting("BreakRange", 5.0, 3.0, 7.0)
            .addSetting("Delay", 15.0, 1.0, 50.0)); // ms

        addMod(new Module("AutoAnchor", "Combat", GLFW.GLFW_KEY_V)
            .addSetting("ExplodeDelay", 60.0, 20.0, 200.0)); // ms

        addMod(new Module("ShieldCracker", "Combat", 0));
        addMod(new Module("Reach", "Combat", 0)
            .addSetting("Distance", 4.2, 3.0, 6.0));
        addMod(new Module("Velocity", "Combat", 0));

        // --- PLAYER / UTILS ---
        addMod(new Module("SmartTotem", "Player", 0));
        addMod(new Module("AutoEat", "Player", 0)
            .addSetting("HealthThreshold", 14.0, 2.0, 20.0));
        addMod(new Module("FastXP", "Player", GLFW.GLFW_KEY_Y)
            .addSetting("ThrowSpeed", 18.0, 5.0, 20.0)); // ticks

        // --- RENDER ---
        addMod(new Module("StorageESP", "Render", 0)
            .addSetting("Range", 64.0, 16.0, 128.0));
        addMod(new Module("FullBright", "Render", 0));
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
            if (m.name.equals("FastXP")) doFastXP(m);
        });
    }

    // ==========================================
    //           AMETHYST MANTIK KODLARI
    // ==========================================

    private static void doAutoCrystal(Module m) {
        long delay = (long) (1000 / m.getSetting("Delay").val);
        if (System.currentTimeMillis() - lastCrystalTime < delay) return;
        
        // Bu versiyon sadece patlatma (Break) üzerine odaklıdır. Place için ek mantık gerekir.
        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.canSee(e) && mc.player.distanceTo(crystal) < m.getSetting("BreakRange").val) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = System.currentTimeMillis();
            }
        });
    }

    private static void doAutoAnchor() {
        Module m = getMod("AutoAnchor");
        if (System.currentTimeMillis() - lastAnchorTime < m.getSetting("ExplodeDelay").val) return;

        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) return;
        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);

        if (anc != -1 && glow != -1) {
            int oldSlot = mc.player.getInventory().selectedSlot;
            // Sırayla paket gönder
            switchToSlot(anc);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            
            switchToSlot(glow);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); // Doldur
            
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit); // Patlat
            
            switchToSlot(oldSlot); // Eski eşyaya dön
            lastAnchorTime = System.currentTimeMillis();
        }
    }

    private static void doShieldCracker() {
        if (System.currentTimeMillis() - lastShieldTime < 600) return; // Spam engelleme
        if (mc.targetedEntity instanceof PlayerEntity target && target.isHolding(Items.SHIELD)) {
            int axe = findAxe();
            if (axe != -1) {
                int oldSlot = mc.player.getInventory().selectedSlot;
                switchToSlot(axe);
                mc.interactionManager.attackEntity(mc.player, target);
                switchToSlot(oldSlot); // Kırdıktan sonra dön
                lastShieldTime = System.currentTimeMillis();
            }
        }
    }

    private static void doFastXP(Module m) {
        // Minecraft saniyede 20 tick çalışır. m.val ticks cinsindendir.
        long ticksSinceLastThrow = 20 - (long)m.getSetting("ThrowSpeed").val;
        if (System.currentTimeMillis() - lastXpTime < ticksSinceLastThrow * 50) return; 

        int xp = findItem(Items.EXPERIENCE_BOTTLE);
        if (xp != -1 && mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            mc.options.useKey.setPressed(true);
            lastXpTime = System.currentTimeMillis();
        } else if (xp != -1) {
            mc.player.getInventory().selectedSlot = xp;
        } else {
             mc.options.useKey.setPressed(false);
        }
    }

    private static void doAutoEat(Module m) {
        if (mc.player.getHungerManager().getFoodLevel() <= m.getSetting("HealthThreshold").val) {
            int food = findFood();
            if (food != -1) {
                mc.player.getInventory().selectedSlot = food;
                mc.options.useKey.setPressed(true);
            }
        } else if (mc.options.useKey.isPressed() && mc.player.getMainHandStack().getComponents().contains(DataComponentTypes.FOOD)) {
            mc.options.useKey.setPressed(false);
        }
    }

    // --- YARDIMCI METOTLAR ---
    public static Module getMod(String name) { return moduleMap.get(name); }
    private static void switchToSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
    }
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
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new AmethystGui());
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled) doAutoAnchor();
    }

    // ==========================================
    //           MODÜL VE AYAR YAPISI
    // ==========================================

    public static class Module {
        public String name, category;
        public int key;
        public boolean enabled = false;
        public Map<String, Setting> settings = new LinkedHashMap<>();

        public Module(String name, String category, int key) {
            this.name = name; this.category = category; this.key = key;
        }
        public Module addSetting(String name, double val, double min, double max) {
            settings.put(name, new Setting(name, val, min, max));
            return this;
        }
        public Setting getSetting(String name) { return settings.get(name); }
        public void toggle() { this.enabled = !this.enabled; }
    }

    public static class Setting {
        public String name;
        public double val, min, max;
        public Setting(String name, double val, double min, double max) {
            this.name = name; this.val = val; this.min = min; this.max = max;
        }
    }

    // ==========================================
    //           AMETHYST (CLICK) GUI
    // ==========================================

    public static class AmethystGui extends Screen {
        private String selectedCat = "Combat";
        private double sliderValue = 0.5; // Test için basit kaydırıcı değeri

        public AmethystGui() { super(Text.literal("Mbest700 Amethyst")); }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // --- ANA ARKA PLAN ---
            context.fill(10, 10, this.width - 10, this.height - 10, COLOR_BG);
            
            // --- BAŞLIK ---
            context.drawText(this.textRenderer, "§l§dMbest700 §r§7- §5Amethyst", 20, 20, COLOR_TEXT, true);
            
            // --- KATEGORİ SEÇİCİ ---
            renderCatSelectors(context);
            
            // --- SEÇİLİ KATEGORİDEKİ MODÜLLER VE AYARLAR ---
            renderModulesAndSettings(context, mouseX);
            
            super.render(context, mouseX, mouseY, delta);
        }

        private void renderCatSelectors(DrawContext context) {
            int x = 20;
            String[] cats = {"Combat", "Player", "Render"};
            for (String cat : cats) {
                int bgColor = cat.equals(selectedCat) ? COLOR_AMETHYST : 0xFF1A1A1A;
                int txtColor = cat.equals(selectedCat) ? COLOR_TEXT : COLOR_TEXT_DIM;
                
                context.fill(x, 40, x + 70, 55, bgColor);
                context.drawText(this.textRenderer, cat, x + 5, 43, txtColor, false);
                x += 75;
            }
        }

        private void renderModulesAndSettings(DrawContext context, int mouseX) {
            int x = 20;
            int yMod = 65;
            
            for (Module m : moduleMap.values()) {
                if (m.category.equals(selectedCat)) {
                    // --- MODÜL AÇ/KAPAT VE TİK KUTUSU ---
                    renderModuleToggle(context, m, x, yMod);
                    yMod += 18;
                    
                    // --- AYARLAR (SLIDERS VE CHECKBOXES) ---
                    if (m.enabled && !m.settings.isEmpty()) {
                        yMod = renderSettingsForModule(context, m, x + 10, yMod, mouseX);
                        yMod += 5; // Modüller arası boşluk
                    }
                }
            }
        }

        private void renderModuleToggle(DrawContext context, Module m, int x, int y) {
            int bgColor = m.enabled ? COLOR_AMETHYST_DARK : 0xFF222222;
            int txtColor = m.enabled ? COLOR_TEXT : COLOR_TEXT_DIM;
            
            // Arka Plan
            context.fill(x, y, x + 150, y + 16, bgColor);
            
            // Amethyst Tik Kutusu (Açıkken dolu mor, kapalıyken gri çerçeve)
            int checkColor = m.enabled ? COLOR_AMETHYST : 0xBB888888;
            context.fill(x + 135, y + 2, x + 147, y + 14, checkColor);
            
            // Modül İsmi
            context.drawText(this.textRenderer, m.name, x + 5, y + 4, txtColor, false);
        }

        private int renderSettingsForModule(DrawContext context, Module m, int x, int y, int mouseX) {
            for (Setting s : m.settings.values()) {
                // Ayar İsmi ve Değeri
                context.drawText(this.textRenderer, s.name, x, y, COLOR_TEXT_DIM, false);
                String valStr = "§7(" + String.format("%.1f", s.val) + ")";
                context.drawText(this.textRenderer, valStr, x + 90, y, COLOR_TEXT, false);
                y += 10;
                
                // Amethyst Slider (Kayırcı)
                double perc = (s.val - s.min) / (s.max - s.min);
                int sliderX = (int)(x + perc * 130);
                
                context.fill(x, y, x + 130, y + 3, 0xFF444444); // Slider Arka Plan (Gri hat)
                context.fill(x, y, sliderX, y + 3, COLOR_AMETHYST); // Dolu Kısım (Mor)
                context.fill(sliderX - 2, y - 2, sliderX + 2, y + 5, COLOR_TEXT); // Kaydırıcı Kafa (Beyaz)
                
                y += 12;
            }
            return y;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // --- KATEGORİ SEÇİMİ ---
            int xCat = 20;
            String[] cats = {"Combat", "Player", "Render"};
            for (String cat : cats) {
                if (mouseX > xCat && mouseX < xCat + 70 && mouseY > 40 && mouseY < 55) {
                    selectedCat = cat;
                    return true;
                }
                xCat += 75;
            }

            // --- MODÜL AÇ/KAPAT ---
            int yMod = 65;
            for (Module m : moduleMap.values()) {
                if (m.category.equals(selectedCat)) {
                    // Modül aç/kapat tıkı
                    if (mouseX > 20 && mouseX < 20 + 150 && mouseY > yMod && mouseY < yMod + 16) {
                        m.toggle();
                        return true;
                    }
                    yMod += 18;

                    // --- AYAR SLIDERLARI ---
                    if (m.enabled && !m.settings.isEmpty()) {
                        yMod = handleSettingsClick(m, 20 + 10, yMod, mouseX, mouseY);
                        yMod += 5;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private int handleSettingsClick(Module m, int x, int y, double mouseX, double mouseY) {
            for (Setting s : m.settings.values()) {
                y += 10;
                // Slider tıkı ve sürükleme
                if (mouseX > x && mouseX < x + 130 && mouseY > y && mouseY < y + 12) {
                    double perc = (mouseX - x) / 130.0;
                    s.val = s.min + perc * (s.max - s.min);
                    s.val = Math.max(s.min, Math.min(s.max, s.val)); // Sınırla
                    return y + 12; // Tıklandıysa döngüyü kes, sürükleme için başka metod lazım
                }
                y += 12;
            }
            return y;
        }

        // --- SÜRÜKLEME DESTEĞİ (Slider'ı fareyle çekmek için) ---
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
             int yMod = 65;
            for (Module m : moduleMap.values()) {
                if (m.category.equals(selectedCat)) {
                    yMod += 18;
                    if (m.enabled && !m.settings.isEmpty()) {
                        yMod = handleSettingsDrag(m, 20 + 10, yMod, mouseX, mouseY);
                        yMod += 5;
                    }
                }
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        private int handleSettingsDrag(Module m, int x, int y, double mouseX, double mouseY) {
             for (Setting s : m.settings.values()) {
                y += 10;
                if (mouseX > x && mouseX < x + 130 && mouseY > y - 10 && mouseY < y + 12) { // Genişletilmiş tık alanı
                    double perc = (mouseX - x) / 130.0;
                    s.val = s.min + perc * (s.max - s.min);
                    s.val = Math.max(s.min, Math.min(s.max, s.val)); // Sınırla
                }
                y += 12;
            }
            return y;
        }

        @Override public boolean shouldPause() { return false; }
    }
        }
                
