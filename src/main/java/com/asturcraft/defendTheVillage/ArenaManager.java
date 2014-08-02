package com.asturcraft.defendTheVillage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

public class ArenaManager
{
	public ConcurrentHashMap<String, Location> locs = new ConcurrentHashMap();
    public final Logger logger = Logger.getLogger("Minecraft");
    
	Map<String, ItemStack[]> inv = new HashMap();
	Map<String, ItemStack[]> armor = new HashMap();

	CopyOnWriteArrayList<Arena> arenas = new CopyOnWriteArrayList();
	int arenaSize = 0;
	Main plugin;
	public String POINTS_NAME = null;
	public String ALIVE_NAME = null;
	public String DEAD_NAME = null;
	public String ZOMBS_NAME = null;
	public String VILLS_NAME = null;

	public ArenaManager(Main pl) {
		this.plugin = pl;
	    this.POINTS_NAME = (ChatColor.DARK_GREEN + pl.config.get("points"));
	    this.ALIVE_NAME = (ChatColor.GOLD + pl.config.get("sb_alive"));
	    this.DEAD_NAME = (ChatColor.GRAY + pl.config.get("sb_dead"));
	    this.ZOMBS_NAME = (ChatColor.RED + pl.config.get("sb_zombies"));
	    this.VILLS_NAME = (ChatColor.GREEN + pl.config.get("sb_villagers"));
	}

	// Obtain an arena object (or null if not found)
	public Arena getArena(int i) {
		for (Arena a : this.arenas) {
			if (a.getId() == i) {
				return a;
			}
		}
		return null;
	}

	// Get players ArrayList of an Arena
	public ArrayList<Player> getPlayersInArena(Arena a) {
		ArrayList<Player> pls = new ArrayList<Player>();
		
		for (String s : a.players) {
			pls.add(Bukkit.getPlayer(s));
		}
		
		return pls;
	}

	// Add a player to a game
	public void addPlayer(final Player p, int i) {
		if ((!p.isOnline()) || (p.isDead()))
			return;
		
		final Arena a = getArena(i);
		if (a == null) {
			p.sendMessage("Invalid arena!");
			return;
		}

		// Can join players now?
		if (!a.canJoin) {
			if (!p.hasPermission("vd.vip")) {
				this.plugin.s(p, this.plugin.config.get("arena_started"));
				this.plugin.s(p, this.plugin.config.get("compra_vip"));
				return;
			}
		}

		// If the player is already in the game, returns
		if (a.getPlayers().contains(p.getName())) {
			return;
		}
		
		// If the arena is full, don't add the player
		if (a.getPlayers().size() >= a.maxPlayers) {
			this.plugin.s(p, this.plugin.config.get("arena_full"));
			return;
		}

		// Add the player and save inventory and armor
		a.getPlayers().add(p.getName());
		this.inv.put(p.getName(), p.getInventory().getContents());
		this.armor.put(p.getName(), p.getInventory().getArmorContents());

		// Clean the player's inventory and armor
		p.getInventory().setArmorContents(null);
		p.getInventory().clear();
		
		// Feed the player and health it.
		p.setFoodLevel(20);
		p.setHealth(20);

		// Save the current location of the player ans teleport him to the arena
		this.locs.put(p.getName(), p.getLocation());
		p.teleport(a.ps);

		// Put the kits book into the inventory
		p.getInventory().addItem(new ItemStack[] { this.plugin.object_kits_book });
		p.updateInventory();

		// Create the score board to the user
		createScoreboard(p, a);

		// Send the notification to the arena
		for (Player pl : getPlayersInArena(a)) {
			this.plugin.s(pl, this.plugin.config.get("waiting_for_players").replace("$1", Integer.toString(a.getPlayers().size())).replace("$2", Integer.toString(a.maxPlayers)));
			this.plugin.s(pl, "El jugador " + p.getName() + " se ha unido a la arena.");
		}

		// If the game isn't started and the player list is full, start the countdown
		if ((!a.notStarted) && (a.getPlayers().size() >= a.maxPlayers)) {
			a.notStarted = true;
			a.counter = this.plugin.getConfig().getInt("config.starting_time");
			
			// Notify the players
			for (Player pl : getPlayersInArena(a)) {
				this.plugin.s(pl, this.plugin.config.get("starting_in").replace("$1", Integer.toString(a.counter)));
			}
		}

		// Update the sign in the lobby
		this.plugin.updateSign(a);

		Main._log("Added player " + p.getName() + " to arena " + a.getId() + " (" + a.pav + ").");
		
		// Update score board
		updateSc(a);
	}

	// Function to remove a player from the game
	public void removePlayer(final Player p) {
		if (!p.isOnline()) // Is the player online?
			return;
		
		// Find the arena in which one the player is in
		Arena a = null;
		for (Arena arena : this.arenas) {
			if (arena.getPlayers().contains(p.getName())) {
				a = arena;
			}
		}

		// If the player isn't in any arena... security error!
		if ((a == null) || (!a.getPlayers().contains(p.getName())))
		{
			Main._logE("Algo ha fallado con el jugador " + p.getName());
			p.kickPlayer("Algo ha fallado...\nPor razones de seguridad te expulso.\nInforma a un admin.");
			return;
		}

		// Remove player from the list of players
		a.getPlayers().remove(p.getName());
		
		// If the player was dead, remove him from that list too
		if (a.deadPlayers.contains(p.getName())) {
			a.deadPlayers.remove(p.getName());
			
			// Can't fly again
			p.setAllowFlight(false);
			p.setFlying(false);
			
			// If the player was dead and hidden from other users, return to be shown
			for (Player pl : Bukkit.getOnlinePlayers()) {
				if (p != pl) {
					pl.showPlayer(p);
				}
			}
		}

		// Clean the player's current inventory and armor
		p.getInventory().clear();
		p.getInventory().setArmorContents(null);

		// Restore original inventory and armor
		p.getInventory().setContents((ItemStack[])this.inv.get(p.getName()));
		p.getInventory().setArmorContents((ItemStack[])this.armor.get(p.getName()));

		// Remove backup data from memory
		this.inv.remove(p.getName());
		this.armor.remove(p.getName());

		// Teleport the player to it's past location
		p.teleport((Location)this.locs.get(p.getName()));

		// Remove the data about it's location
		this.locs.remove(p.getName());

		p.setFireTicks(0);
		
		// If the player is under effect of some potion, remove it
		for(PotionEffect pe : p.getActivePotionEffects()){
			p.removePotionEffect(pe.getType());
		}

		// Remove the score board
		removeScoreboard(p);

		// Notify that a player left the arena
		for (Player pl : getPlayersInArena(a)) {
			this.plugin.s(pl, this.plugin.config.get("left_arena").replace("$1", p.getName()));
		}

		// Update lobby's sign
		this.plugin.updateSign(a);

		Main._log("Quitado jugador " + p.getName() + " de la arena " + a.getId() + " (" + a.pav + ").");

		// If there weren't players, reload the arena to it's original state
		if (a.players.size() == 0)
			this.plugin.reloadArena(a.id);
	}

	// Function to create an Arena
	public Arena createArena(Location z1, Location z2, Location z3, Location v1, Location v2, Location v3, Location ps, Location lobby, String pav, int id, int icon, int mp) {
		this.plugin.getConfig().set("arenas." + id + ".name", pav);
		this.plugin.getConfig().set("arenas." + id + ".z1", serializeLoc(z1));
		this.plugin.getConfig().set("arenas." + id + ".z2", serializeLoc(z2));
		this.plugin.getConfig().set("arenas." + id + ".z3", serializeLoc(z3));
		this.plugin.getConfig().set("arenas." + id + ".v1", serializeLoc(v1));
		this.plugin.getConfig().set("arenas." + id + ".v2", serializeLoc(v2));
		this.plugin.getConfig().set("arenas." + id + ".v3", serializeLoc(v3));
		this.plugin.getConfig().set("arenas." + id + ".ps", serializeLoc(ps));
		this.plugin.getConfig().set("arenas." + id + ".lobby", serializeLoc(ps));
		this.plugin.getConfig().set("arenas." + id + ".icon", Integer.valueOf(icon));
		this.plugin.getConfig().set("arenas." + id + ".maxplayers", Integer.valueOf(mp));
		
		Arena a = new Arena(z1, z2, z3, v1, v2, v3, ps, lobby, pav, id, mp);
		
		this.plugin.saveConfig();
		
		Main._log("Created arena " + a.getId() + " (" + a.pav + ")");
		return a;
	}

	// Function to set a configuration for an arena
	public void setArenaSetup(int id, String setup, Object var) {
		this.plugin.getConfig().set("arenas." + id + "." + setup, var);
		this.plugin.saveConfig();
		
		Main._log("Configured " + setup + "=" + var + " for arena " + id + ".");
	}

	// Function to check if a player is in game
	public boolean isInGame(Player p) {
		for (Arena a : this.arenas) {
			if (a.getPlayers().contains(p.getName()))
				return true;
		}
		return false;
	}
	
	// Function to check if a Zombie is in game
	public boolean isInGame(Zombie z) {
		for (Arena a : this.arenas) {
			if (a.getZombies().contains(z))
				return true;
		}
		return false;
	}

	// Function to serialize a location
	public String serializeLoc(Location l) {
		return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
	}

	// Function to deserialize a location
	public Location deserializeLoc(String s) {
		String[] st = s.split(",");
		return new Location(Bukkit.getWorld(st[0]), Integer.parseInt(st[1]), Integer.parseInt(st[2]), Integer.parseInt(st[3]));
	}

	// Function to start a game
	public void start(Arena a) {
		// It only works when there is a fresh start (wave 0)
		if (a.wave > 0) 
			return;
		
		Main._log("Starting arena " + a.getId() + " (" + a.pav + ").");
		
		// Set some status variables
		a.canJoin = false;
		a.changeKit = false;
		a.wave = 1;
		a.vil = 3.0F;
		a.check = true;

		// Update lobby's sign
		this.plugin.updateSign(a);
		
		for (String s : a.players) {
			if (Bukkit.getPlayer(s) == null) {
				// If the player isn't found, remove it
				a.players.remove(s);
			} else {
				Player p = Bukkit.getPlayer(s);
				
				// Send starting message to the player
				this.plugin.s(p, this.plugin.config.get("starting").replace("$1", Integer.toString(a.wave)));
				
				// If the player hasn't got the emerald, give it to him
				if (!p.getInventory().contains(this.plugin.emerald_item)) {
					p.getInventory().addItem(new ItemStack[] { this.plugin.emerald_item });
				}
				
				// If the player hasn't got the book, give it to him
				if (p.getInventory().contains(this.plugin.object_kits_book)) {
					p.getInventory().removeItem(new ItemStack[] { this.plugin.object_kits_book });
				}
				
				// Create the score board it it doesn't exist
				if (p.getScoreboard().getObjective(DisplaySlot.SIDEBAR) == null)
					createScoreboard(p, a);
				
				// Update score board
				p.getScoreboard().getObjective(DisplaySlot.SIDEBAR).setDisplayName(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + this.plugin.config.get("sb_wave").replace("$1", "1") + ChatColor.DARK_GRAY + " -");
				
				// Update health and food level
				p.setHealth(20);
				p.setFoodLevel(20);
				
				// Add selected kit to the player
				addKit(p);
				
			}
		}

		// Spawn the villagers in random spawn areas of the config
		for (int i = 0; i < a.vil; i++) {
			int r = new Random().nextInt(3);
			
			switch (r) {
			case 0:
				a.villagers.add((Villager)a.v1.getWorld().spawnEntity(a.v1, EntityType.VILLAGER));
				break;
			case 1:
				a.villagers.add((Villager)a.v2.getWorld().spawnEntity(a.v2, EntityType.VILLAGER));
				break;
			case 2:
				a.villagers.add((Villager)a.v3.getWorld().spawnEntity(a.v3, EntityType.VILLAGER));
			}
		}

		// Spawn zombies in random spawn areas of the config
		for (int i = 0; i < a.zomb; i++) {
			Zombie v = null;
			int r = new Random().nextInt(3);
			
			switch (r) {
			case 0:
				a.zombies.add(v = (Zombie)a.z1.getWorld().spawnEntity(a.z1, EntityType.ZOMBIE));
				break;
			case 1:
				a.zombies.add(v = (Zombie)a.z2.getWorld().spawnEntity(a.z2, EntityType.ZOMBIE));
				break;
			case 2:
				a.zombies.add(v = (Zombie)a.z3.getWorld().spawnEntity(a.z3, EntityType.ZOMBIE));
			}
			
			// Increase speed of the zombies
			v.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 1));
		}
		
		// Update the score board
		updateSc(a);
	}

	// Function to compute the next wave of zombies
	public void nextwave(Arena a) {
		// It won't work for the starting wave
		if (a.wave == 0) 
			return;
		
		a.wave += 1;
		a.changeKit = false;
		Main._logD("Next wave (" + a.wave + ") in the arena " + a.getId() + "(" + a.pav + ").");
		
		for (String s : a.players) {
			Player p = Bukkit.getPlayer(s);
			
			if (p == null) {
				// If the player doesn't exist, remove it
				a.players.remove(s);
			} else {
				// Send notification to the player
				this.plugin.s(p, this.plugin.config.get("starting").replace("$1", Integer.toString(a.wave)));
				
				// If the plaser hasn't got the emerald, get it to him
				if (!p.getInventory().contains(this.plugin.emerald_item)) {
					if (this.plugin.getKit(p).equals("hardcore")) {
						//If it's playing in hardcore mode and hasn't got the emerald, it's dead
					}
					else {
						p.getInventory().addItem(new ItemStack[] { this.plugin.emerald_item });
					}
				}
				
				// If the player hasn't got it's kit book, give one to him
				if (p.getInventory().contains(this.plugin.object_kits_book)) {
					p.getInventory().removeItem(new ItemStack[] { this.plugin.object_kits_book });
					addKit(p);
				}
				
				// If the player hasn't got a score board, create it
				if (p.getScoreboard().getObjective(DisplaySlot.SIDEBAR) == null)
					createScoreboard(p, a);
				
				// Update score board
				p.getScoreboard().getObjective(DisplaySlot.SIDEBAR).setDisplayName(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + this.plugin.config.get("sb_wave").replace("$1", new StringBuilder(String.valueOf(a.wave)).toString()) + ChatColor.DARK_GRAY + " -");
			}
		}

		// Do damage to zombies and villagers to kill them in order
		// to respawn them
		for (Zombie z : a.zombies)
			z.damage(9000.0D);
		for (Villager v : a.villagers)
			v.damage(9000.0D);
		
		//Every 5 waves, increase villagers in 4 and zombie bosses in 1
		if (a.wave % 5 == 0) {
			a.vil += 4.0F;
			a.b += 1.0F;
		}
		
		//Increase zombies in every wave
		if (a.wave <= 10) {
			a.zomb = ((float)(a.zomb + 1.3D));
		} else if ((a.wave > 10) && (a.wave <= 20)){
			a.zomb = ((float)(a.zomb + 2.7D)); // x2
		} else if ((a.wave > 20) && (a.wave <= 30)){
			a.zomb = ((float)(a.zomb + 4.1D)); // x3
		} else if (a.wave > 30) {
			a.zomb = ((float)(a.zomb + 5.5D)); // x4
		}
		
		// Update the lobby's sign
		this.plugin.updateSign(a);
		
		// Generate the villagers in random spawn points from config
		for (int i = 0; i < a.vil; i++) {
			int r = new Random().nextInt(3);
			
			switch (r) {
				case 0:
					a.villagers.add((Villager)a.v1.getWorld().spawnEntity(a.v1, EntityType.VILLAGER));
					break;
				case 1:
					a.villagers.add((Villager)a.v2.getWorld().spawnEntity(a.v2, EntityType.VILLAGER));
					break;
				case 2:
					a.villagers.add((Villager)a.v3.getWorld().spawnEntity(a.v3, EntityType.VILLAGER));
			}
		}

		// Generate the zombies in random spawn points from config
		for (int i = 0; i < a.zomb; i++) {
			int r = new Random().nextInt(3);
			Zombie v = null;
			
			switch (r) {
				case 0:
					a.zombies.add(v = (Zombie)a.z1.getWorld().spawnEntity(a.z1, EntityType.ZOMBIE));
					break;
				case 1:
					a.zombies.add(v = (Zombie)a.z2.getWorld().spawnEntity(a.z2, EntityType.ZOMBIE));
					break;
				case 2:
					a.zombies.add(v = (Zombie)a.z3.getWorld().spawnEntity(a.z3, EntityType.ZOMBIE));
			}

			// Customization of zombies
			if (new Random().nextInt(4) == 0)
				v.setBaby(true); // Baby zombies randomized
			else
				v.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 1));
			if ((a.wave > 4) && (a.wave < 17) && (new Random().nextInt(2) == 0))
				v.getEquipment().setItemInHand(new ItemStack(Material.WOOD_SWORD));
			if ((a.wave > 6) && (a.wave < 19) && (new Random().nextInt(2) == 0))
				v.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
			if ((a.wave > 8) && (a.wave < 21) && (new Random().nextInt(2) == 0))
				v.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
			if ((a.wave > 10) && (a.wave < 23) && (new Random().nextInt(2) == 0))
				v.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
			if ((a.wave > 14) && (a.wave < 25) && (new Random().nextInt(2) == 0))
				v.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
			if ((a.wave > 16) && (a.wave < 27) && (new Random().nextInt(2) == 0))
				v.getEquipment().setItemInHand(new ItemStack(Material.IRON_SWORD));
			if ((a.wave > 18) && (a.wave < 29) && (new Random().nextInt(2) == 0))
				v.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
			if ((a.wave > 20) && (a.wave < 31) && (new Random().nextInt(2) == 0))
				v.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
			if ((a.wave > 22) && (a.wave < 33) && (new Random().nextInt(2) == 0))
				v.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
			if ((a.wave > 24) && (a.wave < 35) && (new Random().nextInt(2) == 0))
				v.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
			if ((a.wave > 26) && (a.wave < 37) && (new Random().nextInt(2) == 0))
				v.getEquipment().setItemInHand(new ItemStack(Material.DIAMOND_SWORD));
			if ((a.wave > 28) && (a.wave < 39) && (new Random().nextInt(2) == 0))
				v.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
			if ((a.wave > 30) && (a.wave < 41) && (new Random().nextInt(2) == 0))
				v.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
			if ((a.wave > 32) && (a.wave < 43) && (new Random().nextInt(2) == 0))
				v.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
			if ((a.wave > 34) && (new Random().nextInt(3) == 0))
				v.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
			
			// Give power to the zombies !!
			if ((a.wave > 10) && (a.wave <= 20)){
				v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 1));
			} else if ((a.wave > 20) && (a.wave <= 30)){
				v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 2));
			} else if (a.wave > 30) {
				v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 3));
			}

		}
		
		// Create bosses every 5 waves
		if (a.wave % 5 == 0) { 
			for (int i = 0; i < a.b; i++) {
				int r = new Random().nextInt(3);
				Zombie v = null;
				switch (r) {
					case 0:
						a.zombies.add(v = (Zombie)a.z1.getWorld().spawnEntity(a.z1, EntityType.SKELETON));
						break;
					case 1:
						a.zombies.add(v = (Zombie)a.z2.getWorld().spawnEntity(a.z2, EntityType.SKELETON));
						break;
					case 2:
						a.zombies.add(v = (Zombie)a.z3.getWorld().spawnEntity(a.z3, EntityType.SKELETON));
				}
	
				// Give them speed and absorption increases
				v.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 3));
				v.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 999999999, (a.wave-2)*2));
				
				// Give random objects
				if ((new Random().nextInt(10) == 0))
					v.getEquipment().setItemInHand(new ItemStack(Material.DIAMOND_SWORD));
				if ((new Random().nextInt(2) == 0))
					v.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
				if ((new Random().nextInt(2) == 0))
					v.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
				if ((new Random().nextInt(2) == 0))
					v.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
				if ((new Random().nextInt(2) == 0))
					v.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
				
				// Increase damage depending of the wave number
				if ((a.wave > 10) && (a.wave <= 20)){
					v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 1));
				} else if ((a.wave > 20) && (a.wave <= 30)){
					v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 2));
				} else if (a.wave > 30) {
					v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 3));
				}
				
			}
			
			// Send messages to the players about the bosses
			for (String s : a.players) {
				Player jugador = Bukkit.getPlayer(s);
				if (a.wave == 5) {
					this.plugin.s(jugador, ChatColor.RED + "Un jefe de las hordas de zombies ha hecho acto de presencia... Y está muy muy enfadado...");
				}
				else {
					this.plugin.s(jugador, ChatColor.RED + "Varios jefes de las hordas de zombies han hecho acto de presencia... Y están muy muy enfadados...");
				}
			}
			
			
		}
		
		// Update the score board
		updateSc(a);
	}

	// Function to check the villagers status
	public void checkVillagers(Arena a) {
		Main._logD("Checking villagers in arena " + a.getId() + "(" + a.pav + ").");
		
		if ((a.villagers.size() == 0) && (a.check)) {
			// If no one villager is live, zombie wins
			Main._logD("0 found! Zombies won.");
			
			for (String s : a.players) {
				// Notify the players
				this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("z_win"));
			}
			
			// Reload the arena
			this.plugin.reloadArena(a.id);
		}
	}

	// Function to check the zombies status
	public void checkZombies(Arena a) {
		Main._logD("Checking zombies in arena " + a.getId() + "(" + a.pav + ").");
		
		if ((a.zombies.size() == 0) && (a.check)) {
			Main._log("0 founs! Waiting for nex wave and dead players reliving...");
			
			// Set status variables
			a.waitingNextWave = true;
			a.counter = 10;
			a.changeKit = true;
			int vilag = a.villagers.size();
			
			// Damage villagers to kill them
			for (Villager v : a.villagers) {
				v.damage(99999.0D);
			}
			
			// Notify players
			for (String s : a.players) {
				if (!a.notStarted) {
					this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("v_win").replace("$1", Integer.toString(vilag * 3)).replace("$2", Integer.toString(vilag)));
					
					// Add score to the player
					addScore(Bukkit.getPlayer(s), vilag * 3);
				}
			}
			
			// Relive dead players
			for (String s : a.deadPlayers) {
				Player p = Bukkit.getPlayer(s);
				
				// Check that  the dead player wasn't playing in hardcore mode
				if (!this.plugin.getKit(p).equals("hardcore")) {
					p.setFlying(false);
					p.setAllowFlight(false);

					for (Player pl : Bukkit.getOnlinePlayers()) {
						if (p != pl) {
							pl.showPlayer(p);
						}
					}

					a.deadPlayers.remove(s);
					
					// Add a wood sword to relived
					p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
				}
			}
		}
	}

	// Function to check players status
	public void checkPlayers(Arena a) {
		Main._logD("Checking players in the arena " + a.getId() + "(" + a.pav + ").");
		
		if ((a.players.size() <= a.deadPlayers.size()) && (a.check)) {
			Main._logD("All the players has died! Zombie won!");
			
			for (String s : a.players) {
				// Notify all the players
				this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("p_dead"));
			}
			
			// Reload the arena
			this.plugin.reloadArena(a.id);
		}
	}

	// Function to add the score of a player
	public void addScore(Player pl, int i) {
		pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).setScore(getScore(pl) + i);
	}

	// Function to remove the score of a player
	public void removeScore(Player pl, int i) {
		pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).setScore(getScore(pl) - i);
	}

	// Function to set the score of a player
	public void setScore(Player pl, int i) {
		pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).setScore(i);
	}

	// Function to get the score of a player
	public int getScore(Player pl) {
		return pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).getScore();
	}

	// Function to update the score board
	public void updateSc(Arena a) {
		for (String s : a.getPlayers()) {
			// Update the score board to each user in the arena
			Objective o = Bukkit.getPlayer(s).getScoreboard().getObjective(DisplaySlot.SIDEBAR);
			o.getScore(Bukkit.getOfflinePlayer(this.ALIVE_NAME)).setScore(a.getPlayers().size() - a.deadPlayers.size());
			o.getScore(Bukkit.getOfflinePlayer(this.DEAD_NAME)).setScore(a.deadPlayers.size());
			o.getScore(Bukkit.getOfflinePlayer(this.VILLS_NAME)).setScore(a.villagers.size());
			o.getScore(Bukkit.getOfflinePlayer(this.ZOMBS_NAME)).setScore(a.zombies.size());
		}
	}

	// Create the score board to a user playing in an arena
	public void createScoreboard(Player pl, Arena a) {
		// Only if the user is on-line
		if (!pl.isOnline()) {
			return;
		}
		
		// Create the score board
		Objective objective = Bukkit.getScoreboardManager().getNewScoreboard().registerNewObjective("VillageDef", "dummy");
		objective.setDisplayName(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + this.plugin.config.get("sb_starting") + ChatColor.DARK_GRAY + " -");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		pl.setScoreboard(objective.getScoreboard());

		// Points score
		Score vers = objective.getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME));
		vers.setScore(1);

		// Alive score
		vers = objective.getScore(Bukkit.getOfflinePlayer(this.ALIVE_NAME));
		vers.setScore(a.getPlayers().size() - a.deadPlayers.size());

		// Dead score
		vers = objective.getScore(Bukkit.getOfflinePlayer(this.DEAD_NAME));
		vers.setScore(a.deadPlayers.size());

		// Villagers score
		vers = objective.getScore(Bukkit.getOfflinePlayer(this.VILLS_NAME));
		vers.setScore(a.villagers.size());

		// Zombies score
		vers = objective.getScore(Bukkit.getOfflinePlayer(this.ZOMBS_NAME));
		vers.setScore(a.zombies.size());
	}

	// Function to remove the score board to an user
	public void removeScoreboard(Player pl) {
		if ((pl.getScoreboard() != null) && (pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR) != null))
			pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).unregister();
	}
	
	// Function to give his selected kit to a player.
	public void addKit(Player p) {
		if (this.plugin.getKit(p).equals("tanque")) {
			p.getInventory().addItem(new ItemStack[] {
					new ItemStack(Material.WOOD_SWORD),
					new ItemStack(Material.IRON_CHESTPLATE)	});
		} else if (this.plugin.getKit(p).equals("piromano")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD),
					new ItemStack(Material.LEATHER_HELMET),
					new ItemStack(Material.LEATHER_LEGGINGS)});
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.FIRE_RESISTANCE).toItemStack(1) });
		} else if (this.plugin.getKit(p).equals("bruja")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.SLOWNESS).splash().toItemStack(5) });
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.STRENGTH).toItemStack(1) });
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.SPEED).toItemStack(1) });
		} else if (this.plugin.getKit(p).equals("hardcore")) {
			p.getInventory().addItem(new ItemStack[] {
					new ItemStack(Material.IRON_SWORD),
					new ItemStack(Material.IRON_CHESTPLATE),
					new ItemStack(Material.IRON_LEGGINGS)});
		} else if (this.plugin.getKit(p).equals("arquero")) {
			ItemStack arco = new ItemStack(Material.BOW);
			arco.addUnsafeEnchantment(Enchantment.DAMAGE_ALL, 1);
			p.getInventory().addItem(arco);
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ARROW, 3) });
		} else if (this.plugin.getKit(p).equals("cadete")) {
			ItemStack espada = new ItemStack(Material.WOOD_SWORD);
			espada.addEnchantment(Enchantment.FIRE_ASPECT, 1);
			p.getInventory().addItem(espada);
		} else if (this.plugin.getKit(p).equals("peleador")) {
			//p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, Integer.MAX_VALUE, 2));
		} else if (this.plugin.getKit(p).equals("congelado")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.SNOW_BALL, 16) });
		} else if (this.plugin.getKit(p).equals("parkour")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			ItemStack botas = new ItemStack(Material.GOLD_BOOTS);
			botas.addUnsafeEnchantment(Enchantment.PROTECTION_FALL, 4);
			p.getInventory().addItem(botas);
		} else if (this.plugin.getKit(p).equals("gordito")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.CAKE, 1) });
		} else if (this.plugin.getKit(p).equals("defensa")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			ItemStack pechera_encantada = new ItemStack(Material.LEATHER_CHESTPLATE);
			pechera_encantada.addUnsafeEnchantment(Enchantment.THORNS, 3);
			p.getInventory().addItem(pechera_encantada);
		} else if (this.plugin.getKit(p).equals("corredor")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.SPEED).toItemStack(2) });
		} else if (this.plugin.getKit(p).equals("espadachin")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.IRON_SWORD) });
		} else if (this.plugin.getKit(p).equals("pacifico")) {
			ItemStack espada = new ItemStack(Material.GOLD_SWORD);
			espada.addUnsafeEnchantment(Enchantment.KNOCKBACK, 1);
			p.getInventory().addItem(espada);
		} else if (this.plugin.getKit(p).equals("protegido")) {
			ItemStack pecho = new ItemStack(Material.LEATHER_CHESTPLATE);
			pecho.addUnsafeEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 1);
			p.getInventory().addItem(pecho);
		} else if (this.plugin.getKit(p).equals("experto")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.IRON_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.DIAMOND_CHESTPLATE) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.DIAMOND_HELMET) });
		} else if (this.plugin.getKit(p).equals("sabueso")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(383, 1, (byte)95)});
			p.getInventory().addItem(new ItemStack[] { new ItemStack(383, 1, (byte)95)});
			p.getInventory().addItem(new ItemStack[] { new ItemStack(383, 1, (byte)95)});
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.BONE, 10) });
		} else if (this.plugin.getKit(p).equals("conejo")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
			p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 1));
		} else if (this.plugin.getKit(p).equals("enfermero")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.INSTANT_HEAL).splash().toItemStack(2) });
			p.getInventory().addItem(new ItemStack[] { new Potion(PotionType.REGEN).toItemStack(1) });
		} else if (this.plugin.getKit(p).equals("legolas")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.BOW) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.ARROW, 20) });
		} else if (this.plugin.getKit(p).equals("dorado")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.WOOD_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.GOLDEN_APPLE, 3) });
		} else if (this.plugin.getKit(p).equals("op")) {
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.IRON_SWORD) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.IRON_LEGGINGS) });
			p.getInventory().addItem(new ItemStack[] { new ItemStack(Material.IRON_CHESTPLATE) });
		}
	}

}
