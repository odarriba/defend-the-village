package com.asturcraft.defendTheVillage;

import java.util.ArrayList;

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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public class SeleccionarKit implements Listener {

	private Inventory inv;
	
	public SeleccionarKit(Plugin p) {
		inv = Bukkit.getServer().createInventory(null, 27, "Selecciona tu kit");
		
		//kits
		ItemStack tanque = crearKit(Material.IRON_CHESTPLATE, "Tanque");
		ItemStack piromano = crearKit(Material.FIRE, "Piromano");
		ItemStack bruja = crearKit(Material.POTION, "Bruja");
		ItemStack hardcore = crearKit(Material.SKULL_ITEM, "Hardcore");
		ItemStack arquero = crearKit(Material.BOW, "Arquero");
		ItemStack cadete = crearKit(Material.WOOD_SWORD, "Cadete");
		ItemStack peleador = crearKit(Material.SKULL_ITEM, "Peleador");
		ItemStack congelado = crearKit(Material.SNOW_BALL, "Congelado");
		ItemStack parkour = crearKit(Material.FEATHER, "Parkour");
		ItemStack gordito = crearKit(Material.CAKE, "Gordito");
		ItemStack defensa = crearKit(Material.LEATHER_CHESTPLATE, "Defensa");
		ItemStack corredor = crearKit(Material.FEATHER, "Corredor");
		ItemStack espadachin = crearKitVIP(Material.IRON_SWORD, "Espadachin");
		ItemStack pacifico = crearKitVIP(Material.STICK, "Pacifico");
		ItemStack protegido = crearKitVIP(Material.LEATHER_HELMET, "Protegido");
		ItemStack experto = crearKitVIP(Material.DIAMOND_HELMET, "Experto");
		ItemStack sabueso = crearKitVIP(Material.BONE, "Sabueso");
		ItemStack conejo = crearKitVIP(Material.FEATHER, "Conejo");
		ItemStack enfermero = crearKitVIP(Material.POTION, "Enfermero");
		ItemStack legolas = crearKitVIP(Material.BOW, "Legolas");
		ItemStack manzana_dorada = crearKitVIP(Material.GOLDEN_APPLE, "Dorado");
		ItemStack op = crearKitVIP(Material.IRON_LEGGINGS, "OP");
		
		inv.setItem(0, tanque);
		inv.setItem(1, piromano);
		inv.setItem(2, bruja);
		inv.setItem(3, hardcore);
		inv.setItem(4, arquero);
		inv.setItem(5, cadete);
		inv.setItem(6, peleador);
		inv.setItem(7, congelado);
		inv.setItem(8, parkour);
		inv.setItem(9, gordito);
		inv.setItem(10, defensa);
		inv.setItem(11, corredor);
		inv.setItem(12, espadachin);
		inv.setItem(13, pacifico);
		inv.setItem(14, protegido);
		inv.setItem(15, experto);
		inv.setItem(16, sabueso);
		inv.setItem(17, conejo);
		inv.setItem(18, enfermero);
		inv.setItem(19, legolas);
		inv.setItem(20, manzana_dorada);
		inv.setItem(21, op);

		
		Bukkit.getServer().getPluginManager().registerEvents(this, p);
	}
	
	private ItemStack crearKit(Material objeto, String nombre) {
		ItemStack kit = new ItemStack(objeto, 1);
		ItemMeta meta = (ItemMeta) kit.getItemMeta();
		ArrayList<String> lore = new ArrayList<String>();
		lore.add("Kit de " + nombre);
		meta.setLore(lore);
		meta.setDisplayName("" + nombre);
		kit.setItemMeta(meta);
		return kit;
	}
	
	private ItemStack crearKitVIP(Material objeto, String nombre) {
		ItemStack kit = new ItemStack(objeto, 1);
		ItemMeta meta = (ItemMeta) kit.getItemMeta();
		ArrayList<String> lore = new ArrayList<String>();
		lore.add("SOLO VIP");
		meta.setLore(lore);
		meta.setDisplayName("" + nombre);
		kit.setItemMeta(meta);
		return kit;
	}
	
	public void show(Player p) {
		p.openInventory(inv);
	}
}


