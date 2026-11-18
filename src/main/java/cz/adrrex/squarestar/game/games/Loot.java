package cz.adrrex.squarestar.game.games;

import cz.adrrex.squarestar.SquareStar;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.Random;

/** Náhodné itemy pro truhly (SkyWars) a Lucky Tower. */
final class Loot {
    private Loot() {}

    static ItemStack random(Random r, boolean good) {
        int roll = r.nextInt(100) + (good ? 25 : 0);
        var items = SquareStar.I.items;
        if (roll < 18) return new ItemStack(pick(r, Material.COBBLESTONE, Material.OAK_PLANKS, Material.RED_TERRACOTTA, Material.END_STONE), 16 + r.nextInt(17));
        if (roll < 30) return new ItemStack(pick(r, Material.COOKED_BEEF, Material.BREAD, Material.GOLDEN_CARROT), 3 + r.nextInt(4));
        if (roll < 40) return new ItemStack(pick(r, Material.STONE_SWORD, Material.WOODEN_AXE, Material.STONE_AXE, Material.STONE_PICKAXE));
        if (roll < 50) return new ItemStack(pick(r, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_HELMET, Material.CHAINMAIL_BOOTS, Material.GOLDEN_CHESTPLATE, Material.CHAINMAIL_LEGGINGS));
        if (roll < 57) return new ItemStack(pick(r, Material.SNOWBALL, Material.EGG), 8 + r.nextInt(9));
        if (roll < 62) { ItemStack bow = new ItemStack(Material.BOW); return r.nextInt(3) == 0 ? bow : new ItemStack(Material.ARROW, 6 + r.nextInt(10)); }
        if (roll < 67) return new ItemStack(pick(r, Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.FISHING_ROD, Material.COBWEB));
        if (roll < 72) return items.get(pick(r, "meteor_grenade", "moon_rock", "space_ration"), 2 + r.nextInt(2));
        if (roll < 78) return potion(r);
        if (roll < 84) return new ItemStack(pick(r, Material.IRON_SWORD, Material.IRON_CHESTPLATE, Material.IRON_HELMET, Material.IRON_LEGGINGS, Material.IRON_BOOTS, Material.IRON_PICKAXE));
        if (roll < 89) return new ItemStack(pick(r, Material.GOLDEN_APPLE, Material.ENDER_PEARL, Material.TNT, Material.FLINT_AND_STEEL));
        if (roll < 94) return items.get(pick(r, "jetpack", "gravity_boots", "oxygen_helmet"), 1);
        if (roll < 100) { ItemStack s = new ItemStack(pick(r, Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_HELMET)); if (r.nextBoolean()) ench(s); return s; }
        if (roll < 110) return items.get(pick(r, "laser_pistol", "plasma_sword"), 1);
        return new ItemStack(pick(r, Material.DIAMOND_SWORD, Material.DIAMOND_BOOTS, Material.DIAMOND_LEGGINGS, Material.TOTEM_OF_UNDYING));
    }

    private static void ench(ItemStack s) {
        if (s.getType().name().endsWith("SWORD")) s.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
        else s.addUnsafeEnchantment(Enchantment.PROTECTION, 1);
    }

    private static ItemStack potion(Random r) {
        ItemStack it = new ItemStack(r.nextBoolean() ? Material.SPLASH_POTION : Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.setBasePotionType(pick(r, PotionType.SWIFTNESS, PotionType.HEALING, PotionType.REGENERATION, PotionType.STRENGTH, PotionType.LEAPING, PotionType.SLOW_FALLING));
        it.setItemMeta(m);
        return it;
    }

    @SafeVarargs static <T> T pick(Random r, T... a) { return a[r.nextInt(a.length)]; }
}
