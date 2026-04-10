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
    private static long lastCrystalTime = 0;

    @Override
    public void onInitializeClient() {
        init();
    }

    public static void init() {
        addMod(new Module("AutoCrystal", GLFW.GLFW_KEY_C, 16.0)); 
        addMod(new Module("AutoAnchor", GLFW.GLFW_KEY_V, 0));
        addMod(new Module("SmartTotem", 0, 1.0));
        addMod(new Module("Surround", GLFW.GLFW_KEY_X, 0));
        addMod(new Module("Reach", 0, 4.2));
        addMod(new Module("Velocity", 0, 0.0));
        addMod(new Module("ShieldCracker", 0, 0));
        addMod(new Module("AutoEat", 0, 14.0));
    }

    private static void addMod(Module m) { moduleMap.put(m.name, m); }

    public static void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (getMod("SmartTotem").enabled) doSmartTotem();
        if (getMod("AutoCrystal").enabled) doAutoCrystal();
        if (getMod("Velocity").enabled && mc.player.hurtTime > 0) mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        if (getMod("AutoEat").enabled) doAutoEat();
        if (getMod("ShieldCracker").enabled) doShieldCracker();
    }

    public static void onKey(int key) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) mc.setScreen(new ClickGui());
        if (key == GLFW.GLFW_KEY_V && getMod("AutoAnchor").enabled) doAutoAnchor();
    }

    private static void doAutoCrystal() {
        Module m = getMod("AutoCrystal");
        long delay = (long) (1000 / m.val) + new Random().nextInt(15);
        if (System.currentTimeMillis() - lastCrystalTime < delay) return;
        mc.world.getEntities().forEach(e -> {
            if (e instanceof EndCrystalEntity crystal && mc.player.canSee(e) && mc.player.distanceTo(crystal) < getMod("Reach").val) {
                mc.getNetworkHandler().sendPacket(PlayerInteractEntityC2SPacket.attack(crystal, false));
                mc.player.swingHand(Hand.MAIN_HAND);
                lastCrystalTime = System.currentTimeMillis();
            }
        });
    }

    private static void doSmartTotem() {
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            int slot = findItem(Items.TOTEM_OF_UNDYING);
            if (slot != -1) mc.interactionManager.clickSlot(0, slot < 9 ? slot + 36 : slot, 45, SlotActionType.SWAP, mc.player);
        }
    }

    private static void doAutoAnchor() {
        HitResult hit = mc.crosshairTarget;
        if (!(hit instanceof BlockHitResult bHit)) return;
        int anc = findItem(Items.RESPAWN_ANCHOR);
        int glow = findItem(Items.GLOWSTONE);
        if (anc != -1 && glow != -1) {
            mc.player.getInventory().selectedSlot = anc;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            mc.player.getInventory().selectedSlot = glow;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bHit);
        }
    }

    private static void doShieldCracker() {
        if (mc.targetedEntity instanceof PlayerEntity target && target.isHolding(Items.SHIELD)) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                    mc.player.getInventory().selectedSlot = i;
                    mc.interactionManager.attackEntity(mc.player, target);
                    break;
                }
            }
        }
    }

    private static void doAutoEat() {
        if (mc.player.getHungerManager().getFoodLevel() <= getMod("AutoEat").val) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.getComponents().contains(DataComponentTypes.FOOD)) {
                    mc.player.getInventory().selectedSlot = i;
                    mc.options.useKey.setPressed(true);
                    return;
                }
            }
        }
    }

    public static Module getMod(String name) { return moduleMap.get(name); }
    private static int findItem(net.minecraft.item.Item item) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static class Module {
        public String name; public int key; public boolean enabled = false; public double val;
        public Module(String name, int key, double val) { this.name = name; this.key = key; this.val = val; }
        public void toggle() { this.enabled = !this.enabled; }
    }

    public static class ClickGui extends Screen {
        public ClickGui() { super(Text.literal("Mbest700")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(20, 20, 240, 300, 0xDD000000);
            int y = 55;
            for (Module m : moduleMap.values()) {
                context.drawText(this.textRenderer, (m.enabled ? "§a" : "§c") + m.name, 30, y, 0xFFFFFF, false);
                y += 18;
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int y = 55;
            for (Module m : moduleMap.values()) {
                if (mouseX > 30 && mouseX < 200 && mouseY > y && mouseY < y + 15) {
                    if (button == 0) m.toggle();
                    return true;
                }
                y += 18;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
