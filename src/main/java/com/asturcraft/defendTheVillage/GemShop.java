package com.asturcraft.defendTheVillage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionType;

public class GemShop implements Listener {

	private Inventory inv;
	public List<ItemStack> objectList;
	
	public GemShop(Plugin p) {
		inv = Bukkit.getServer().createInventory(null, 36, "Compra objetos");

		//Objetos
		this.objectList = new ArrayList<ItemStack>();
		this.objectList.add(createObject(Material.DIAMOND_BOOTS,1,"Botas de diamante",null,null,175));
		this.objectList.add(createObject(Material.DIAMOND_CHESTPLATE,1,"Pechera de diamante",null,null,220));
		this.objectList.add(createObject(Material.DIAMOND_LEGGINGS,1,"Pantalones de diamante",Enchantment.PROTECTION_ENVIRONMENTAL,1,270));
		this.objectList.add(createObject(Material.DIAMOND_BOOTS,1,"Botas de diamante",Enchantment.PROTECTION_ENVIRONMENTAL,1,300));
		this.objectList.add(createObject(Material.DIAMOND_HELMET,1,"Casco de diamante",Enchantment.THORNS,1,275));
		this.objectList.add(createObject(Material.DIAMOND_LEGGINGS,1,"Pantalones de diamante",Enchantment.THORNS,2,290));
		this.objectList.add(createObject(Material.DIAMOND_CHESTPLATE,1,"Pechera de diamante",Enchantment.DURABILITY,1,290));
		this.objectList.add(createObject(Material.DIAMOND_LEGGINGS,1,"Pantalones de diamante",Enchantment.DURABILITY,1,275));
		this.objectList.add(createObject(Material.DIAMOND_HELMET,1,"Casco de diamante",Enchantment.PROTECTION_ENVIRONMENTAL,3,340));
		this.objectList.add(createObject(Material.IRON_CHESTPLATE,1,"Pechera de hierro",null,null,125));
		this.objectList.add(createObject(Material.IRON_BOOTS,1,"Botas de hierro",null,null,90));
		this.objectList.add(createObject(Material.IRON_BOOTS,1,"Botas de hierro",Enchantment.PROTECTION_ENVIRONMENTAL,3,190));
		this.objectList.add(createObject(Material.IRON_LEGGINGS,1,"Pantalones de hierro",Enchantment.DURABILITY,1,175));
		this.objectList.add(createObject(Material.GOLD_HELMET,1,"Casco de oro",Enchantment.PROTECTION_ENVIRONMENTAL,4,225));
		this.objectList.add(createObject(Material.LEATHER_CHESTPLATE,1,"Pechera de cuero",Enchantment.PROTECTION_ENVIRONMENTAL,3,125));
		this.objectList.add(createObject(Material.IRON_SWORD,1,"Espada de hierro",null,null,165));
		this.objectList.add(createObject(Material.IRON_SWORD,1,"Espada de hierro",Enchantment.KNOCKBACK,1,240));
		this.objectList.add(createObject(Material.IRON_SWORD,1,"Espada de hierro",Enchantment.DAMAGE_ALL,1,240));
		this.objectList.add(createObject(Material.WOOD_SWORD,1,"Espada de madera",null,null,75));
		this.objectList.add(createObject(Material.WOOD_SWORD,1,"Espada de madera",Enchantment.DURABILITY,2,105));
		this.objectList.add(createObject(Material.WOOD_SWORD,1,"Espada de madera",Enchantment.DAMAGE_ALL,1,140));
		this.objectList.add(createObject(Material.IRON_SWORD,1,"Espada de hierro",Enchantment.FIRE_ASPECT,2,175));
		this.objectList.add(createObject(Material.DIAMOND_SWORD,1,"Espada de diamante",Enchantment.DAMAGE_ALL,1,475));
		this.objectList.add(createObject(Material.DIAMOND_SWORD,1,"Espada de diamante",null,null,440));
		this.objectList.add(createObject(Material.STONE_AXE,1,"Hacha de piedra",Enchantment.DAMAGE_ALL,3,175));
		this.objectList.add(createObject(Material.IRON_AXE,1,"Hacha de hierro",Enchantment.DAMAGE_ALL,1,250));
		this.objectList.add(createObject(Material.WOOD_AXE,1,"Hacha de madera",Enchantment.DAMAGE_ALL,4,190));
		this.objectList.add(createObject(Material.BOW,1,"Arco",null,null,75));
		this.objectList.add(createObject(Material.ARROW,10,"Flechas",null,null,25));
		this.objectList.add(createObject(Material.GOLDEN_APPLE,1,"Manzana dorada",null,null,50));
		this.objectList.add(createObject(Material.COOKED_BEEF,5,"Filete de ternera",null,null,75));
		this.objectList.add(createObject(Material.APPLE,2,"Manzana",null,null,25));
		
		Integer i = 0;
		Iterator<ItemStack> itr = this.objectList.iterator();
		while (itr.hasNext()) {
			inv.setItem(i, itr.next());
			i++;
		}
		
		Bukkit.getServer().getPluginManager().registerEvents(this, p);
	}
	
	public ItemStack createObject(Material objeto, Integer cantidad, String nombre,Enchantment encantamiento, Integer nivelencantamiento, Integer valor) {
		ItemStack item = new ItemStack(objeto, cantidad);
		ItemMeta meta = (ItemMeta) item.getItemMeta();
		ArrayList<String> lore = new ArrayList<String>();
		lore.add("" + valor + " gemas.");
		meta.setLore(lore);
		meta.setDisplayName(nombre);
		item.setItemMeta(meta);
		
		if (encantamiento != null) {
			item.addEnchantment(encantamiento, nivelencantamiento);
		}

		return item;
	}
	
	public ItemStack createPotion(PotionType poti, String nombre, Integer valor) {
		ItemStack poti2 = new Potion(poti).splash().toItemStack(3);
		ItemMeta meta = (ItemMeta) poti2.getItemMeta();
		ArrayList<String> lore = new ArrayList<String>();
		lore.add("" + valor + " gemas.");
		meta.setLore(lore);
		meta.setDisplayName(nombre);
		poti2.setItemMeta(meta);

		return poti2;
	}
	
	public void show(Player p) {
		p.openInventory(inv);
	}
	
}