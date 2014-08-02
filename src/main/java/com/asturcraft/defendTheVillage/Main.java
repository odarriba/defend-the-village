package com.asturcraft.defendTheVillage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_7_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_7_R3.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_7_R3.entity.CraftZombie;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.Potion;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public class Main extends JavaPlugin implements Listener {
	public ReadConfiguration config = null;
	public static Main plugin = null;
	public static boolean debug = false;
	public ArenaManager am = null;

	public ItemStack object_kits_book = new ItemStack(Material.ENCHANTED_BOOK);
	ItemMeta book_meta = this.object_kits_book.getItemMeta();
	ArrayList<String> book_description = new ArrayList();
	public ItemStack emerald_item = new ItemStack(Material.EMERALD);
	ItemMeta emerald_item_meta = this.emerald_item.getItemMeta();
	ArrayList<String> emdescription = new ArrayList();
	ArrayList<String> allowedCommands = new ArrayList();
	
	//menu de los kits
	public static SelectKit selectKit;
	
	//menu de los objetos
	public GemShop gemShop;

	public void onEnable() {
		Main.plugin = this;
		Main.debug = false;
		
		_log("Enabling...");
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		// If the configuration doesn't exist, copy from resources
		if (!new File(getDataFolder(), "config.yml").exists()){
			_log("Configuration not found! Regenerating...");
			saveResource("config.yml", false);
		}
		
		// Create common objects
		this.config = new ReadConfiguration(this);
		this.am = new ArenaManager(this);
		
		// Book of kits
		this.book_meta.setDisplayName(this.config.get("kit_book"));
		this.book_description.add(ChatColor.DARK_GREEN + this.config.get("kit_book_desc"));
		this.book_meta.setLore(this.book_description);
		this.object_kits_book.setItemMeta(this.book_meta);
		
		// Emerald shop
		this.emerald_item_meta.setDisplayName(this.config.get("emerald_shop"));
		this.emdescription.add(ChatColor.DARK_GREEN + this.config.get("emerald_shop_desc"));
		this.emerald_item_meta.setLore(this.emdescription);
		this.emerald_item.setItemMeta(this.emerald_item_meta);

		// Load Arena configuration
		loadArenaConfig();
		
	    // SelectKit to be able to select the kits
	    this.selectKit = new SelectKit(this);
	    
	    // GemShop to generate the inventory view of the shop
	    this.gemShop = new GemShop(this);
		
		_logD("DEBUG INFO IS ENABLED!");

		// Task of count down to start arenas and new waves
		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				for (Arena a : Main.this.am.arenas) {
					// If the arena isn't started
					if (a.notStarted) {
						if (a.counter == 0) {
							// Counter is 0. Start now!
							Main.this.am.start(a);
							a.notStarted = false;
						} else {
							// Count down
							a.counter -= 1;
							
							// Every 5 seconds tell the players
							if (a.counter % 5 == 0) {
								for (String s : a.players)
									Main.this.s(Bukkit.getPlayer(s), Main.plugin.config.get("starting_in").replace("$1", Integer.toString(a.counter)));
							}
						}
					}
					else if (a.waitingNextWave) { // If the arena is waiting for other wave
						if (a.counter == 0) {
							// Counter is 0. Next wave coming!
							Main.this.am.nextwave(a);
							a.waitingNextWave = false;
						} else {
							// Count down!
							a.counter -= 1;
						}
					}
				}
			}
		}, 20L, 20L);
		
		// Task to check zombies, players and villagers statuses
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				// Check zombies
				for (Arena a : Main.this.am.arenas) {
					// Only check if the game it's playing
					if ((!a.notStarted) && (!a.waitingNextWave)) {
						boolean b = false;
						
						// Check if there is any kind of zombie entity
						for (Entity e : a.z1.getWorld().getEntities()) {
							if ((e instanceof Zombie) || (e instanceof Skeleton)) {
								b = true;
							}
						}
						
						// If there is no zombies, clear the zombies ArrayList
						if (!b)
							a.zombies.clear();
						
						// Check zombies status
						Main.this.am.checkZombies(a);
					}
				}
				
				Entity e;
				
				// Check villagers
				for (Arena a : Main.this.am.arenas) {
					if ((!a.notStarted) && (!a.waitingNextWave)) {
						boolean b = false;
						Iterator itr = a.v1.getWorld().getEntities().iterator();
						
						// Check if there is any villager entity
						while (itr.hasNext()) {
							e = (Entity) itr.next();
							if ((e instanceof Villager)) {
								b = true;
							}
						}
						
						// If there isn't villagers, clear the villagers ArrayList
						if (!b)
							a.villagers.clear();
						
						// Check villagers status
						Main.this.am.checkVillagers(a);
					}
				}
				
				// Check players
				for (Arena a : Main.this.am.arenas){
					if ((!a.notStarted) && (!a.waitingNextWave)) {
						// Check existence of players
						for (String s : a.getPlayers()) {
							Player p = Bukkit.getPlayer(s);
							
							// If the player is null, remove from player's ArrayList
							if (p == null)
								a.getPlayers().remove(s);
						}
						
						// Check players status
						Main.this.am.checkPlayers(a);
					}
				}
			}
		}, 200L, 200L);
	}

	// Load arena configuration
	private void loadArenaConfig()
	{
		// Load allowed commands
		if (getConfig().getList("config.allowed_commands") != null) {
			this.allowedCommands = ((ArrayList)getConfig().getList("config.allowed_commands"));
		}
		
		// Load debug configuration
		if ((getConfig().getString("debug") != null) && (getConfig().getString("debug").equals("true"))){
			Main.debug = true;
		}
		
		// Load arenas configuration
		if (getConfig().getConfigurationSection("arenas") != null) {
			Iterator iterator = getConfig().getConfigurationSection("arenas").getKeys(false).iterator();
			
			while (iterator.hasNext()) {
				String arena = (String)iterator.next();
				int id = 0;
				
				try {
					id = Integer.parseInt(arena);
				} catch (Exception localException) { }
				
				// Arena name
				String name = getConfig().getString("arenas." + id + ".name");

				// Load zombie and villagers spawn points
				Location z1 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z1"));
				Location z2 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z2"));
				Location z3 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z3"));
				Location v1 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v1"));
				Location v2 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v2"));
				Location v3 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v3"));
				Location ps = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".ps"));
				Location lobby = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".lobby"));
				
				// Check existence of all the desired worlds
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".ps")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".ps").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".ps").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z1")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".z1").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".z1").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z2")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".z2").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".z2").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z3")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".z3").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".z3").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v1")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".v1").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".v1").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v2")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".v2").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".v2").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v3")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".v3").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".v3").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".lobby")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".lobby").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".lobby").toString()).split(",")[0] + "not found. Importing!");
				}
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".sign")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".sign").split(",")[0]));
					_log("WARNING! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".sign").toString()).split(",")[0] + "not found. Importing!");
				}
				
				// Load max number of players
				int mp = getConfig().getInt("arenas." + id + ".maxplayers");
				
				// Create the Arena object and add it
				Arena arenaname = new Arena(z1, z2, z3, v1, v2, v3, ps, lobby, name, id, mp);
				this.am.arenas.add(arenaname);

				// Get the sign configuration
				if (getConfig().contains("arenas." + id + ".sign")) {
					Location sign = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".sign"));
					arenaname.sign = sign;
					updateSign(arenaname);
				}
			}
		}
		try {
			getConfig().save(getDataFolder() + System.getProperty("file.separator") + "config.yml");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Function to reload a finished arena
	public synchronized void reloadArena(final int id) {
		_log("Reloading arena " + id + ".");
		
		if (this.am.arenas.contains(this.am.getArena(id))) {
			Arena a = this.am.getArena(id);
			
			// Remove dead players
			for (String s : a.deadPlayers) {
				Player p = Bukkit.getPlayer(s);
				
				// Don't fly anymore
				p.setFlying(false);
				p.setAllowFlight(false);

				// Back to be shown
				for (Player pl : Bukkit.getOnlinePlayers()) {
					if (p != pl) {
						pl.showPlayer(p);
					}
				}
				
				a.deadPlayers.remove(s);
			}
			
			// Remove zombies, skeletons and villagers
			for (Entity e : a.ps.getWorld().getEntities()) {
				if ((e.getType().equals(EntityType.ZOMBIE)) || (e.getType().equals(EntityType.SKELETON)) || (e.getType().equals(EntityType.VILLAGER))) {
					e.remove();
				}
			}
			
			// Remove the players from the arena
			for (String s : this.am.getArena(id).getPlayers()) {
				Player p = Bukkit.getPlayer(s);
				
				// Set tanque kit to avoid being hardcore
				setKit(p, "tanque");
				this.am.removePlayer(p);
			}
			
			// Don't check this arena anymore
			a.check = false;

			// Remove arena object from memory
			Bukkit.getScheduler().scheduleSyncDelayedTask(Main.plugin, new Runnable() {
				public void run() {
					am.arenas.remove(am.getArena(id));
				}
			}, 20L);
		}

		// Load arena again from config
		if (getConfig().getConfigurationSection("arenas." + id) != null) {
			String name = getConfig().getString("arenas." + id + ".name");
			Location z1 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z1"));
			Location z2 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z2"));
			Location z3 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".z3"));
			Location v1 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v1"));
			Location v2 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v2"));
			Location v3 = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".v3"));
			Location ps = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".ps"));
			Location lobby = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".lobby"));
			int mp = getConfig().getInt("arenas." + id + ".maxplayers");
			
			Arena arenaname = new Arena(z1, z2, z3, v1, v2, v3, ps, lobby, name, id, mp);
			this.am.arenas.add(arenaname);

			if (getConfig().contains("arenas." + id + ".sign")) {
				Location sign = this.am.deserializeLoc(getConfig().getString("arenas." + id + ".sign"));
				arenaname.sign = sign;
				updateSign(arenaname);
			}
		}
	}

	// Function to handle disble of the plug-in
	public void onDisable() {
		for (Arena a : this.am.arenas) {
			// Reload every arena
			reloadArena(a.getId());
		}
	}

	// Function to convert an array into a string
	public String arrayToString(String[] args, int offset) {
		String s = "";
		
		for (int i = 0; i <= args.length; i++) {
			if (i > offset) {
				s = s + args[(i - 1)] + " ";
			}
		}
		
		return s;
	}

	// Function to check arguments
	public boolean checkArgs(String s) {
		return ((s.equalsIgnoreCase("z1")) || (s.equalsIgnoreCase("z2")) || (s.equalsIgnoreCase("z3")) || (s.equalsIgnoreCase("v1")) || 
				(s.equalsIgnoreCase("v2")) || (s.equalsIgnoreCase("v3")) || (s.equalsIgnoreCase("name")) || 
				(s.equalsIgnoreCase("ps")) || (s.equalsIgnoreCase("lobby")) || (s.equalsIgnoreCase("maxplayers")));
	}

	// Function to check arguments of integers
	public boolean checkArgsInt(String s) {
		return s.equalsIgnoreCase("maxplayers");
	}

	// Function to handle commands from users
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("abandonar")) {
			// User want's to quit
			if ((sender instanceof Player)) {
				Player pl = (Player)sender;
				
				if (this.am.isInGame(pl)) {
					// If it's in game, quit
					this.am.removePlayer(pl);
					s(sender, this.config.get("left"));
				} else {
					// If not
					s(sender, this.config.get("need_to_play"));
				}
			} else {
				s(sender, "Este comando solo lo puede ejecutar un jugador.");
			}
		}

		if (cmd.getName().equalsIgnoreCase("dlv")) {
			if ((args.length > 0) && (args[0].equalsIgnoreCase("abandonar"))) {
				// User want's to quit
				if ((sender instanceof Player)) {
					Player pl = (Player)sender;
					
					if (this.am.isInGame(pl)) {
						// If it's in game, quit
						this.am.removePlayer(pl);
						s(sender, this.config.get("left"));
					} else {
						// If not, show a message
						s(sender, this.config.get("need_to_play"));
					}
				} else {
					s(sender, "This command can only be issued by a player.");
				}
			}
			else if (sender.hasPermission("dlv.command")) { // The user has permission?
				// Check number of arguments received
				if (args.length > 1) {
					// Create arena command
					if (args[0].equalsIgnoreCase("crear")) {
						if ((sender instanceof Player)) {
							int id = 0;
							
							// ID must be an integer
							try {
								id = Integer.parseInt(args[1]);
							} catch (Exception e) {
								s(sender, "La ID debe ser un numero.");
								return false;
							}
							
							// ID must be from 0 to 26
							if ((id < 0) && (id > 26)) {
								s(sender, "Por favor elige una ID de 0 a 26.");
								return false;
							}
							
							// The ID is in use?
							if (isArena(id)) {
								s(sender, "Esa ID ya esta en uso.");
								return false;
							}
							
							Player p = (Player)sender;
							
							// Create the arena
							this.am.createArena(p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), id + "_arena", id, 1, 10);
							s(p, "Arena numero" + id + " creada correctamente. Configurala con /dlv configurar.");
						} else {
							s(sender, "This command can only be issued by a player.");
						}
					}
					else if (args[0].equalsIgnoreCase("configurar")) {
						// Check arguments length
						if (args.length < 3) {
							s(sender, "Uso: /dlv configurar <id_de_la_arena> <configuracion> [argumentos]");
							return false;
						}
						
						// Is the ID a number?
						int id = 0;
						try {
							id = Integer.parseInt(args[1]);
						} catch (Exception e) {
							s(sender, "La ID debe ser un numero.");
							return false;
						}
						
						// Does the arena exist?
						if (getConfig().getConfigurationSection("arenas." + args[1]) == null) {
							s(sender, "ID de arena no encontrada.");
							return false;
						}
						
						// Get the variable name
						args[2] = args[2].toLowerCase();
						if (checkArgs(args[2])) {
							// Its setting a location?
							if ((args[2].toLowerCase().startsWith("v")) || (args[2].toLowerCase().startsWith("z")) || (args[2].equalsIgnoreCase("ps")) || (args[2].equalsIgnoreCase("lobby"))) {
								if ((sender instanceof Player)) {
									Player p = (Player)sender;
									
									// Use player's location
									this.am.setArenaSetup(id, args[2], this.am.serializeLoc(p.getLocation()));
									
									// Reload the arena
									reloadArena(id);
									
									s(sender, "Cambiada correctamente la configuracion de \"" + args[2] + "\" a \"" + this.am.serializeLoc(p.getLocation()) + "\".");
								} else {
									s(sender, "This command can only be issued by a player.");
								}
							} else {
								// Check arguments length
								if (args.length < 4) {
									s(sender, "Esta configuracion requiere argumentos.");
									return false;
								}
								
								if (checkArgsInt(args[2])) {
									try {
										int idas = Integer.parseInt(args[3]);
										
										// Save the value
										this.am.setArenaSetup(id, args[2], Integer.valueOf(idas));
										
										// Reload the arena
										reloadArena(id);
										
										s(sender, "Cambiada correctamente la configuracion de \"" + args[2] + "\" a \"" + idas + "\".");
										return false;
									} catch (Exception e) {
										s(sender, "La ID debe ser un numero.");
										return false;
									}
								}
								// Save arena setup
								this.am.setArenaSetup(id, args[2], args[3]);
								
								// Reload the arena
								reloadArena(id);
								
								s(sender, "Cambiada correctamente la configuracion de \"" + args[2] + "\" a \"" + args[3] + "\".");
							}
						}
						else s(sender, "Revisa tu configuracion.");
					}
				}
				else if (args.length == 1) { // Help message
					if (args[0].equalsIgnoreCase("crear")) {
						s(sender, "Crea tu arena con el comando: /dlv crear <id_de_la_arena>");
						s(sender, "La ID de la arena debe ser de 0 a 26.");
						s(sender, "Despues configurala con: /dlv configurar");
					} else if (args[0].equalsIgnoreCase("configurar")) {
						s(sender, "Configura tu arena con el comando: /dlv configurar <id_de_la_arena> <configuracion> [argumentos]");
						s(sender, "Ejemplo: /dlv configurar 1 name PrimeraArena");
						s(sender, "Configuraciones:");
						s(sender, "z1 ... z3 - configura los 3 puntos de spawn de zombies. Se configura en tu posicion actual.");
						s(sender, "v1 ... v3 - configura los 3 puntos de spawn de aldeanos. Se configura en tu posicion actual.");
						s(sender, "ps - configura el punto de spawn de los jugadores. Se configura en tu posicion actual.");
						s(sender, "lobby - configura el punto de spawn de los jugadores muertos. Debe de estar algo alejado. Yo recomiendo un sitio algo ;).");
						s(sender, "name - configura el nombre de la arena. Necesita el argumento: <nombre>");
						s(sender, "maxplayers - configura el numero maximo de jugadores. Necesita el argumento: <jugadores_maximo(solo numeros)>");
					} else {
						s(sender, "Prueba /dlv.");
					}
				} else {
					s(sender, "Bienvenidos a Defiende la Villa.");
					s(sender, "Comandos disponibles:");
					s(sender, "/dlv crear - escribelo para mas informacion.");
					s(sender, "/dlv configurar - escribelo para mas informacion.");
					s(sender, "/dlv abandonar - salir del juego.");
				}
			}
			else s(sender, this.config.get("no_perm"));
		}
		return false;
	} 
	
	// Function to send a message to an user
	void s(CommandSender s, String ss) {
		s.sendMessage("["+ChatColor.RED + "DLV" + ChatColor.WHITE + "] " + ss);
	}

	// Function to send a log to the server
	public static void _log(String s) {
		Bukkit.getLogger().info("[" + Main.plugin.getDescription().getName() + "] " + s);
	}
	
	// Function to send a debug log to the server
	public static void _logD(String s) {
		if (Main.debug) {
			_log(s);
		}
	}

	// Function to send an error log to the server
	public static void _logE(String s) {
		Bukkit.getLogger().log(Level.SEVERE, "[" + plugin.getDescription().getName() + "] " + s);
	}

	// Function to check if an id is from an arena
	public boolean isArena(int id) {
		for (Arena a : this.am.arenas)
			if (a.id == id)
				return true;
		return false;
	}

	// Function to set the metadata of an user about it's selected kit
	public void setKit(Player jugador, String s) {
		jugador.setMetadata("dlvkit", new FixedMetadataValue(this, s));
	}

	// Function to get the metadata of an user about it's selected kit
	public String getKit(Player pl) {
		return ((MetadataValue)pl.getMetadata("dlvkit").get(0)).asString();
	}

	// Function to update the lobby's signs
	public void updateSign(Arena a) {
		// Check if the arena has a sign asigned
		if (a.sign != null) {
			Sign s = (Sign)a.sign.getBlock().getState();
			String name = a.pav;
			
			// If the name is too longer, cut it
			if (name.length() > 16)
				name = name.substring(0, 16);
			
			s.setLine(0, ChatColor.DARK_RED + a.pav);
			s.setLine(2, ChatColor.GREEN + "" + a.players.size() + ChatColor.GREEN + "/" + ChatColor.GREEN + a.maxPlayers);
			
			// Status of the arena
			if (a.canJoin) {
				s.setLine(1,ChatColor.DARK_PURPLE +  this.config.get("sb_starting"));
				s.setLine(3,ChatColor.BLUE + this.config.get("sign"));
			} else {
				s.setLine(1,ChatColor.DARK_PURPLE + this.config.get("sb_wave").replace("$1", Integer.toString(a.wave)));
				s.setLine(3,ChatColor.DARK_BLUE + this.config.get("sign_full"));
			}
			s.update();
		}
	}

	// Function to handle Sign changes
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onChange(SignChangeEvent ev) {
		if (ev.getLine(0).equals("[dlv]")) {
			int aId = 0;
			
			// Check that the ID is a integer
			try {
				aId = Integer.parseInt(ev.getLine(1));
			} catch (Exception e) {
				s(ev.getPlayer(), "La ID no es un numero.");
				return;
			}
			
			// That integer is a valid arena?
			if (isArena(aId)) {
				this.am.setArenaSetup(aId, "sign", this.am.serializeLoc(ev.getBlock().getLocation()));
				reloadArena(aId);
				s(ev.getPlayer(), "Configurado correctamente \"sign\" a \"" + this.am.serializeLoc(ev.getBlock().getLocation()) + "\".");
			} else {
				s(ev.getPlayer(), "No existe ninguna arena con esa ID.");
			}
		}
	}

	// Function to handle entity deads
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onD(EntityDeathEvent ev) {
		// Is a villager?
		if (ev.getEntityType().equals(EntityType.VILLAGER)) {
			Villager v = (Villager)ev.getEntity();
			
			// Get the arena of the villager
			for (Arena a : this.am.arenas) {
				if (a.villagers.contains(v)) {
					// Remove it
					a.villagers.remove(v);
					
					// Update score board
					this.am.updateSc(a);
					
					// Is it waiting for the next wave?
					if (a.waitingNextWave)
						// Between waves all the villagers got killed
						return;
					
					for (String s : a.players)
						// Notify the users
						s(Bukkit.getPlayer(s), this.config.get("vill_death").replace("$1", Integer.toString(a.villagers.size())));
					
					// Check the villagers status
					this.am.checkVillagers(a);
				}
			}
		}

		// Is a zombie or boss?
		if (ev.getEntityType().equals(EntityType.ZOMBIE) || ev.getEntityType().equals(EntityType.SKELETON)) {
			Entity z = (Entity)ev.getEntity();
			
			// Find the arena of the zombie
			for (Arena a : this.am.arenas)
				if (a.zombies.contains(z)) {
					// Remove from zombies ArrayList
					a.zombies.remove(z);
					
					// Dont drop objects nor experience
					ev.getDrops().clear();
					ev.setDroppedExp(0);
					
					// Only drop an emerald for the shop
					ItemStack emerald = new ItemStack(Material.EMERALD, 1);
					ev.getDrops().add(emerald);
					
					// Update the scoreboard
					this.am.updateSc(a);
					
					// If it's waiting for the next wave, do nothing
					// because villagers and zombies are killed during
					// the waiting
					if (a.waitingNextWave)
						return;
					
					// Check zombies status
					this.am.checkZombies(a);

					// Find the killer player to give him points
					if ((ev.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent)) {
						EntityDamageByEntityEvent nev = (EntityDamageByEntityEvent)ev.getEntity().getLastDamageCause();
						
						if ((nev.getDamager() instanceof Player)) {
							Player p = (Player)nev.getDamager();
							
							// If the user is vip, give him more points
							if (p.hasPermission("dlv.vip")) {
								int ran = new Random().nextInt(15) + 16;
								this.am.addScore(p, ran);
								
								// Notify the player
								s(p, this.config.get("kill").replace("$1", Integer.toString(ran)));
							} else {
								int ran = new Random().nextInt(15) + 1;
								this.am.addScore(p, ran);
								
								// Notify the player
								s(p, this.config.get("kill").replace("$1", Integer.toString(ran)));
							}
						}
					}
				}
		}
	}
	
	// Function to handle the pick-up of objects
	@EventHandler(priority=EventPriority.HIGHEST)
	public void getEmerald(PlayerPickupItemEvent ev){
		 Player player = ev.getPlayer();
		 
		 if (!this.am.isInGame(player)){
			 // If the player isn't playing, do nothing
			 return;
		 }
		 else {
			 // If the player is gaming, check a few things more
			 for (Arena arena : this.am.arenas) {
				 // If the player is dead, can't get emeralds.
				if (arena.getPlayers().contains(player.getName())) {
					if (arena.deadPlayers.contains(player.getName())) {
						ev.setCancelled(true);
						return;
					}
				}
			 }
			 
			 if (ev.getItem().getItemStack().getType() == Material.EMERALD) { 
				 // If the player get an emerald, he gets 10 gems in reward
				 Integer cantidad = ev.getItem().getItemStack().getAmount();
				 this.am.addScore(player, 10*cantidad); //10 points for the player
				 player.playSound(player.getLocation(), Sound.NOTE_PLING, 100.9F, 100.9F);
				 
				 if (cantidad > 1) {
					 s(player, "Has recogido varias gemas. +" + 10*cantidad + " Gemas");
					 player.playSound(player.getLocation(), Sound.NOTE_PLING, 100.9F, 100.9F);
				 }
				 else {
					 s(player, "Has recogido una gema. +" + 10*cantidad + " Gemas");
					 player.playSound(player.getLocation(), Sound.NOTE_PLING, 100.9F, 100.9F);
				 }
				 
				 player.playSound(player.getLocation(), Sound.NOTE_PLING, 100.9F, 100.9F);
				 ev.getItem().remove();
			 }

			 // Cancel the event (the player is still gaming)
			 ev.setCancelled(true);
		 }
	}

	// Function to handle the death of a player
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onDeath(PlayerDeathEvent ev) {
		// Is the player in game?
		if (this.am.isInGame((Player)ev.getEntity())) {
			// Dont' drop anything
			ev.getDrops().clear();
			ev.setDeathMessage(null);
			
			// Find the arena were the player is playing
			for (Arena a : this.am.arenas) {
				if (a.players.contains(ev.getEntity().getName())) {
					for (Zombie z : a.zombies) {
						// Send all the killer zombies to kill the villagers
						if (z.getTarget().equals(ev.getEntity())) {
							CraftZombie z2 = (CraftZombie) z;
							z2.getHandle().setGoalTarget(((CraftLivingEntity) a.villagers.get(0)).getHandle());
						}
					}
				}
			}
		}
	}

	// Function to handle player's kick event
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onKick(PlayerKickEvent ev)
	{
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)) {
			// If the player is in game, remove it to avoid get locked
			// in the arena.
			this.am.removePlayer(player);
		}
	}
	
	// Function to handle respawn events
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onRespawn(PlayerRespawnEvent ev)
	{
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)) {
			// Search in which arena the user is playing
			for (Arena a : this.am.arenas) {
				if (a.players.contains(player.getName())) {
					// Found the arena!
					if ((!a.waitingNextWave) && (!a.notStarted) && (!a.deadPlayers.contains(ev.getPlayer().getName()))) {
						_log("Poniendo al jugador en modo spectator");
						// If there aren't waiting for the next wave, the game has started and the player wasn't dead, put him in
						// spectator mode.
						a.deadPlayers.add(player.getName());
						
						for (Player p : Bukkit.getOnlinePlayers()) {
							if (p != player) {
								p.hidePlayer(player);
							}
						}

						// Ability to fly activated
						player.setAllowFlight(true);
						player.setFlying(true);
						
						// Try to teleport the player to it's location
						ev.setRespawnLocation(player.getLocation());
						
						// Notify other players about the dead of this player
						for (String s : a.getPlayers()) {
							if (Bukkit.getPlayer(s) != null)
								s(Bukkit.getPlayer(s), this.config.get("died").replace("$1", ev.getPlayer().getName()));
						}
						
						// Remove potion effects in the player
						for(PotionEffect pe : ev.getPlayer().getActivePotionEffects()){
							ev.getPlayer().removePotionEffect(pe.getType());
						}
						
						// Update score board
						this.am.updateSc(a);
					}
					
					// Check players status
					this.am.checkPlayers(a);
				}
			}
		}
	}

	// Function to handle the leave of a player from the server
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onLeave(PlayerQuitEvent ev) {
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)) {
			// Remove it to avoid being locked in
			// an arena world.
			this.am.removePlayer(player);
		}
	}

	// Function to handle the drop of objects
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDrop(PlayerDropItemEvent ev) {
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)){
			// In game won't drop anything
			ev.setCancelled(true);
		}
	}

	// Function to avoid commands not allowed in game
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCommandPre(PlayerCommandPreprocessEvent ev) {
		if (this.am.isInGame(ev.getPlayer())) {
			// Only if the player is in game
			boolean c = true;
			
			// Is it allowed?
			for (String com : this.allowedCommands) {
				if (ev.getMessage().toLowerCase().startsWith("/" + com)) {
					c = false;
				}
			}
			if (c)
				// If the command is not allowed, notify the user
				s(ev.getPlayer(), this.config.get("no_command_in_game"));
			ev.setCancelled(c);
		}
	}

	// Function to handle the event of target of the zombies
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onTarget(EntityTargetEvent ev) {
		if ((ev.getTarget() instanceof Player)) {
			Player pl = (Player)ev.getTarget();
			
			// Is the player in game?
			if (this.am.isInGame(pl)) {
				for (Arena a : this.am.arenas) {
					if ((a.deadPlayers.contains(pl.getName())) || (a.notStarted) || (a.waitingNextWave)) {
						// If the player is dead, cancel the targetting
						ev.setCancelled(true);
					}
				}
			}
		}
	}

	//Function to handle the hunger (disable it) in game
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onHunger(FoodLevelChangeEvent ev) {
		if ((ev.getEntity() instanceof Player)) {
			Player pl = (Player)ev.getEntity();
			
			// Is the player in game?
			if (this.am.isInGame(pl)) {
				ev.setCancelled(true);
			}
		}
	}

	// Function to handle the damage to entities
	@EventHandler(priority = EventPriority.HIGHEST)
	public void entityDamage(EntityDamageEvent ev) {
		if ((ev.getEntity() instanceof Player)) {
			Player pl = (Player)ev.getEntity();
			
			if (this.am.isInGame(pl)) {
				for (Arena a : this.am.arenas) {
					if ((a.deadPlayers.contains(pl.getName())) || (a.notStarted)) {
						// Dead players cannot damage entities
						ev.setCancelled(true);
					}
				}
			}
			else if (ev.getCause().equals(EntityDamageEvent.DamageCause.VOID)) {
				// If the player fall into the void, cancel the damage and teleport him
				ev.setCancelled(true);
				pl.teleport(pl.getWorld().getSpawnLocation());
			}
		}
		else if ((ev.getCause().equals(EntityDamageEvent.DamageCause.FIRE)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.POISON)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.WITHER)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.MAGIC))) {
			// Players and Villagers cannot be damaged by fire, potions or magic during game.
			if (ev.getEntity() instanceof Player) {
				// Check that the player is in game
				if (this.am.isInGame((Player)ev.getEntity())) {
					ev.getEntity().setFireTicks(0);
					ev.setCancelled(true);
				}
			}
			else if (ev.getEntity() instanceof Villager){
				// Check that the villager is in game
				for (Arena a : this.am.arenas) {
					if (a.villagers.contains(ev.getEntity())){
						ev.getEntity().setFireTicks(0);
						ev.setCancelled(true);
					}
				}
			}
		}
	}

	// Function to handle combustion event to avoid the zombies firing during sun time
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityCombust(EntityCombustEvent event) {
		if ((event.getEntity() instanceof Zombie))
			// Only apply if the zombie is in an arena world
			if (this.am.isInGame((Zombie) event.getEntity())) {
				event.setCancelled(true);
		}
	}

	//Function to handle damage in order to avoid PVP or PVV
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onDamage(EntityDamageByEntityEvent ev) {
		if ((ev.getDamager() instanceof Player)) {
			Player pl = (Player)ev.getDamager();
			
			// Is in game?
			if (this.am.isInGame(pl)) {
				if (((ev.getEntity() instanceof Villager)) || ((ev.getEntity() instanceof Player))) {
					// Cancel damage
					ev.setCancelled(true);
				}
				
				for (Arena a : this.am.arenas) {
					if (a.deadPlayers.contains(pl.getName())) {
						// Avoid damage by dead players
						ev.setCancelled(true);
					}
				}
			}
		}
		else if (ev.getDamager() instanceof Arrow){ // To avoid damage by arrow
			if (((ev.getEntity() instanceof Villager)) || ((ev.getEntity() instanceof Player))) {
				ev.setCancelled(true);
			}
        }
	}

	// Function to handle interactions to avoid trading during game
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInterEnt(PlayerInteractEntityEvent ev) {
		if (((ev.getRightClicked() instanceof Villager)) && (this.am.isInGame(ev.getPlayer())))
			ev.setCancelled(true);
	}

	// Function to handle interactions of the players
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEvent ev) {
		Player jugador = ev.getPlayer();
		
		if (this.am.isInGame(ev.getPlayer()))
		{
			Iterator<Arena> localObject1 = this.am.arenas.iterator();
			while (localObject1.hasNext()) {
				Arena a = localObject1.next();
				if (a.deadPlayers.contains(ev.getPlayer().getName())) {
					// If the player is dead, cancel the event
					ev.setCancelled(true);
				}
			}
			
			// Click using the kits book
			if (jugador.getItemInHand().equals(this.object_kits_book)) {
				this.selectKit.show(jugador);
				ev.setCancelled(true);
			}
			
			// Click using the emerald shop item
			if (jugador.getItemInHand().equals(this.emerald_item)) {
				this.gemShop.show(jugador);
				ev.setCancelled(true);
			}
		}
		
		// Check for sign clicking
		if (ev.getClickedBlock() != null) {
			Iterator<Arena> localObject2 = this.am.arenas.iterator();
			while (localObject2.hasNext()) {
				Arena a = localObject2.next();
				if ((a.sign != null) && (a.sign.equals(ev.getClickedBlock().getLocation()))) {
					// Add player to the game
					this.am.addPlayer(ev.getPlayer(), a.id);
					
					// Cancel the event
					ev.setCancelled(true);
				}
			}
		}
	}
	
	// Function to handle inventory click in order to select the kit
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInventoryClickEvent (InventoryClickEvent event) {
		Player player = (Player) event.getWhoClicked();
	
		// Is the select kit window?
		if ((event.getInventory().getType() == InventoryType.CHEST) && (event.getInventory().getName().equals("Selecciona tu kit"))){
			
			int slot = event.getRawSlot();
			
			if ((slot > 21) || (slot == -999)) {
				// Cancel the click in slots without kits
				event.setCancelled(true);
				return;
			}
			
			if (event.getCurrentItem().getItemMeta() == null) return;
			
			// Avoid changing the kit in the middle of a game
			for (Arena a: this.am.arenas) {
				if (a.players.contains(player.getName())){
					if (player.hasPermission("dlv.vip")) {
						// Only to VIP users
						if (!a.changeKit) {
							s(player, "Solo puedes cambiar de kit en el descanso entre oleadas.");
							event.setCancelled(true);
							player.closeInventory();
							return;
						}
					}
					else {
						if (!a.notStarted) {
							event.setCancelled(true);
							player.closeInventory();
							return;
						}
					}
				}
			}

			if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Tanque")) {
				Main.this.setKit(player, "tanque");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Piromano")) {
				Main.this.setKit(player, "piromano");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Bruja")) {
				Main.this.setKit(player, "bruja");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Hardcore")) {
				Main.this.setKit(player, "hardcore");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Arquero")) {
				Main.this.setKit(player, "arquero");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Cadete")) {
				Main.this.setKit(player, "cadete");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Peleador")) {
				Main.this.setKit(player, "peleador");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Congelado")) {
				Main.this.setKit(player, "congelado");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Parkour")) {
				Main.this.setKit(player, "parkour");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Gordito")) {
				Main.this.setKit(player, "gordito");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Defensa")) {
				Main.this.setKit(player, "defensa");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Corredor")) {
				Main.this.setKit(player, "corredor");
                Main.this.s(player, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Espadachin")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "espadachin");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Pacifico")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "pacifico");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Protegido")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "protegido");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Experto")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "experto");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Sabueso")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "sabueso");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Conejo")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "conejo");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Enfermero")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "enfermero");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Legolas")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "legolas");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("OP")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "op");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Dorado")) {
				if (player.hasPermission("dlv.vip")) {
					Main.this.setKit(player, "dorado");
					Main.this.s(player, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(player, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				player.closeInventory();
			}
		}
		// Emerald shop
		else if ((event.getInventory().getType() == InventoryType.CHEST) && (event.getInventory().getName().equals("Compra objetos"))) {
			int slot = event.getRawSlot();
			
			if ((slot > 31) || (slot == -999)) {
				event.setCancelled(true);
				return;
			}
			
			if (event.getCurrentItem().getItemMeta() == null){
				event.setCancelled(true);
				return; // If it's nothing, come back
			}
			
			boolean playing = false;
			
			// Check if the user is playing
			for (Arena a: this.am.arenas) {
				if (a.players.contains(player.getName())){
					playing = true;
					break;
				}
			}
			
			// If it isn't playing close the inventary and cancel the event
			if (!playing){
				event.setCancelled(true);
				player.closeInventory();
				return;
			}

			Integer i = 0;
			
			List<ItemStack> copia = this.gemShop.objectList;
			Iterator<ItemStack> itr = copia.iterator();
			
			while (itr.hasNext()) {
				if (i == slot) {
					ItemStack object = itr.next();
					ItemMeta meta = (ItemMeta) object.getItemMeta();
					String valueString = meta.getLore().toString().replace(" puntos.", "").replace("[", "").replace("]", "");
					Integer value = Integer.parseInt(valueString);
					Integer points = this.am.getScore(player);
					
					// Check that the player has enought points
					if (value > points) {
						s(player, "No tienes puntos suficientes");
						event.setCancelled(true);
						player.closeInventory();
						break;
					}
					else {
						this.am.setScore(player, points-value);
						player.getInventory().addItem(object);
						event.setCancelled(true);
						player.closeInventory();
						break;
					}
				}
				else {
					itr.next();
					i++;
				}
			}
		}
		
		else { // Other
			if (event.getCurrentItem() == null) return;
			else if (event.getCurrentItem().getType() == Material.ENCHANTED_BOOK) {
				event.setCancelled(true);
				return;
			}
			else if (event.getCurrentItem().getType() == Material.EMERALD) {
				event.setCancelled(true);
				return;
			}
		}
	}
	
}