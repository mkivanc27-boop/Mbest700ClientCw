package com.mbest700;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.*;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.component.DataComponentTypes;

import java.util.LinkedHashMap;
import java.util.Map;

public class Mbest700 implements ClientModInitializer {

    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static Map<String, Module> moduleMap = new LinkedHashMap<>();

    // Cooldownlar
    private static long lastCrystalTime = 0;
    private static long lastAnchorTime  = 0;
    private static long lastSurroundTime = 0;

    // --- MODÜL YAPISI ---
    public static class Module {
        public String name;
        public String category;
        public boolean enabled;
        public double delay;

        public Module(String name, String category, double delay) {
            this.name = name;
            this.category = category;
            this.enabled = false;
            this.delay = delay;
        }

        public void toggle() { enabled = !enabled; }
    }

    @Override
    public void onInitializeClient() {
        init();
    }

    private static void init() {
        // Combat
        addMod(new Module("AutoCrystal",   "Combat", 0.05)); // CPvP için delay düşürüldü
        addMod(new Module("AutoAnchor",    "Combat", 0.1));
        addMod(new Module("Surround",      "Combat", 0.05)); // Ayaklarına obsidian koyar
        addMod(new Module("AutoTotem",     "Combat", 0.0));  
        addMod(new Module("KillAura",      "Combat", 0.1));

        // Player & Render
        addMod(new Module("AutoEat",       "Player", 0.1));
        addMod(new Module("Velocity",     "Player", 0.0));
        addMod(new Module("FullBright",   "Render", 0.0));
    }

    private static void addMod(Module mod) { moduleMap.put(mod.name, mod); }
    public static Module getMod(String name) { return moduleMap.get(name); }

    // --- TICK DÖNGÜSÜ ---
    public static void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("AutoAnchor").enabled)  doAutoAnchor();
        if (getMod("Surround").enabled)    doSurround();
        if (getMod("AutoTotem").enabled)   doAutoTotem();
        if (getMod("AutoEat").enabled)     doAutoEat();
    }

    // --- CPVP MODÜLLERİ ---

    private static void doAutoCrystal() {
        long now = System.currentTimeMillis();
        if (now - lastCrystalTime < (getMod("AutoCrystal").delay * 1000)) return;

        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal && mc.player.distanceTo(crystal) <= 6) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = now;
                break;
            }
        }
    }

    private static void doSurround() {
        long now = System.currentTimeMillis();
        if (now - lastSurroundTime < 50) return;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos[] surroundBlocks = {
            playerPos.north(), playerPos.south(), playerPos.east(), playerPos.west()
        };

        int obbySlot = findItem(Items.OBSIDIAN);
        if (obbySlot == -1) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        
        for (BlockPos pos : surroundBlocks) {
            if (mc.world.getBlockState(pos).isReplaceable()) {
                switchToSlot(obbySlot);
                // Basit bir blok koyma işlemi (Paket gönderimi eklenebilir)
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, 
                    new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastSurroundTime = now;
            }
        }
        switchToSlot(oldSlot);
    }

    private static void doAutoTotem() {
        if (mc.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;

        int totemSlot = findItem(Items.TOTEM_OF_UNDYING);
        if (totemSlot != -1) {
            // Envanter slotu 9'dan küçükse hotbar'dadır, değilse ana envanterdedir
            int slotIndex = totemSlot < 9 ? totemSlot + 36 : totemSlot;
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotIndex, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    private static void doAutoAnchor() {
        // Anchor mekanizması: Anchor koy -> Glowstone ile doldur -> Patlat
        // Bu modül sunucu hızına göre paketlerle optimize edilmelidir.
    }

    // --- YARDIMCI METOTLAR ---

    private static void switchToSlot(int slot) {
        if (slot != -1 && slot < 9) {
            mc.player.getInventory().selectedSlot = slot;
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    private static int findItem(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) return i;
        }
        return -1;
    }

    private static void doAutoEat() {
        if (mc.player.getHungerManager().getFoodLevel() <= 16) {
            int foodSlot = -1;
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).contains(DataComponentTypes.FOOD)) {
                    foodSlot = i;
                    break;
                }
            }
            if (foodSlot != -1) {
                mc.player.getInventory().selectedSlot = foodSlot;
                mc.options.useKey.setPressed(true);
            }
        } else {
            mc.options.useKey.setPressed(false);
        }
    }
}
