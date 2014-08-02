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

		Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				for (Arena a : Main.this.am.arenas) {
					if (a.noHaEmpezado) {
						if (a.contador == 0) {
							Main.this.am.start(a);
							a.noHaEmpezado = false;
						} else {
							a.contador -= 1;
							if (a.contador % 5 == 0) {
								//Cada 5 segundos aviso a los jugadores
								for (String s : a.jugadores)
									Main.this.s(Bukkit.getPlayer(s), Main.plugin.config.get("starting_in").replace("$1", Integer.toString(a.contador)));
							}
						}
					}
					else if (a.esperandoSiguienteOleada) {
						if (a.contador == 0) {
							Main.this.am.nextwave(a);
							a.esperandoSiguienteOleada = false;
						} else {
							a.contador -= 1; //restamos 1 al contador
						}
					}
				}
			}
		}, 20L, 20L);
		
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
			public void run() {
				for (Arena a : Main.this.am.arenas) {
					if ((!a.noHaEmpezado) && (!a.esperandoSiguienteOleada)) {
						boolean b = false;
						
						for (Entity e : a.z1.getWorld().getEntities()) {
							if ((e instanceof Zombie)) {
								b = true;
							}
						}
						
						if (!b)
							a.zombies.clear();
						
						Main.this.am.checkZombies(a);
					}
				}
				
				Entity e;
				
				for (Arena a : Main.this.am.arenas) {
					if ((!a.noHaEmpezado) && (!a.esperandoSiguienteOleada)) {
						boolean b = false;
						Iterator itr = a.v1.getWorld().getEntities().iterator();
						
						while (itr.hasNext()) {
							e = (Entity) itr.next();
							if ((e instanceof Villager)) {
								b = true;
							}
						}
						
						if (!b)
							a.aldeanos.clear();
						
						Main.this.am.checkVillagers(a);
					}
				}
				
				for (Arena a : Main.this.am.arenas){
					if ((!a.noHaEmpezado) && (!a.esperandoSiguienteOleada)) {
						for (String s : a.getPlayers()) {
							Player p = Bukkit.getPlayer(s);
							
							if (p == null)
								a.getPlayers().remove(s);
						}
						
						Main.this.am.checkPlayers(a);
					}
				}
			}
		}, 200L, 200L);
	}

	private void loadArenaConfig()
	{
		if ((this.config.cu) && (getConfig().getList("config.allowed_commands") != null)) {
			this.allowedCommands = ((ArrayList)getConfig().getList("config.allowed_commands"));
		}
		
		if ((getConfig().getString("debug") != null) && (getConfig().getString("debug").equals("true"))){
			Main.debug = true;
		}
		
		if (getConfig().getConfigurationSection("arenas") != null) {
			Iterator iterator = getConfig().getConfigurationSection("arenas").getKeys(false).iterator();
			
			while (iterator.hasNext()) {
				String arena = (String)iterator.next();
				int id = 0;
				
				try {
					id = Integer.parseInt(arena);
				} catch (Exception localException) { }
				
				String name = getConfig().getString("arenas." + id + ".name");
				
				if (this.am.deserializeLoc(getConfig().getString("arenas." + id + ".ps")).getWorld() == null) {
					Bukkit.createWorld(new WorldCreator(getConfig().getString("arenas." + id + ".ps").split(",")[0]));
					_log("! World " + getConfig().getString(new StringBuilder("arenas.").append(id).append(".ps").toString()).split(",")[0] + "not found. Importing!");
				}

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
		try {
			getConfig().save(getDataFolder() + System.getProperty("file.separator") + "config.yml");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void reloadArena(final int id) {
		_log("Reloading arena " + id + ".");
		
		if ((this.config.cu) && (this.am.arenas.contains(this.am.getArena(id)))) {
			Arena a = this.am.getArena(id);
			
			for (String s : a.jugadoresmuertos) {
				Player p = Bukkit.getPlayer(s);
				p.setFlying(false);
				p.setAllowFlight(false);

				for (Player pl : Bukkit.getOnlinePlayers()) {
					if (p != pl) {
						pl.showPlayer(p);
					}
				}
				
				a.jugadoresmuertos.remove(s);
			}

			for (Zombie s : this.am.getArena(id).zombies) {
				s.remove();
			}
			
			for (Villager s : this.am.getArena(id).aldeanos) {
				s.remove();
			}
			
			for (Entity e : a.ps.getWorld().getEntities()) {
				if ((e.getType().equals(EntityType.ZOMBIE)) || (e.getType().equals(EntityType.VILLAGER))) {
					e.remove();
				}
			}
			
			for (String s : this.am.getArena(id).getPlayers()) {
				Player p = Bukkit.getPlayer(s);
				setKit(p, "tanque");
				this.am.removePlayer(p);
			}
			
			a.check = false;

			Bukkit.getScheduler().scheduleSyncDelayedTask(Main.plugin, new Runnable() {
				public void run() {
					am.arenas.remove(am.getArena(id));
				}
			}, 20L);
		}

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
			
			_log("! If you see any errors above, check arena: " + id + " !");
		}
	}
	//FIN de reloadArena

	public void onDisable() {
		for (Arena a : this.am.arenas) {
			reloadArena(a.getId());
		}
	}

	public String arrayToString(String[] args, int offset) {
		String s = "";
		
		for (int i = 0; i <= args.length; i++) {
			if (i > offset) {
				s = s + args[(i - 1)] + " ";
			}
		}
		
		return s;
	}

	public boolean checkArgs(String s) {
		return ((s.equalsIgnoreCase("z1")) || (s.equalsIgnoreCase("z2")) || (s.equalsIgnoreCase("z3")) || (s.equalsIgnoreCase("v1")) || 
				(s.equalsIgnoreCase("v2")) || (s.equalsIgnoreCase("v3")) || (s.equalsIgnoreCase("name")) || 
				(s.equalsIgnoreCase("ps")) || (s.equalsIgnoreCase("lobby")) || (s.equalsIgnoreCase("maxplayers")));
	}

	public boolean checkArgsInt(String s) {
		return s.equalsIgnoreCase("maxplayers");
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if ((cmd.getName().equalsIgnoreCase("abandonar")) && (this.config.cu)) {
			if ((sender instanceof Player)) {
				Player pl = (Player)sender;
				if (this.am.isInGame(pl)) {
					this.am.removePlayer(pl);
					s(sender, this.config.get("left"));
				} else {
					s(sender, this.config.get("need_to_play"));
				}
			} else {
				s(sender, "Este comando solo lo puede ejecutar un jugador.");
			}
		}

		if ((cmd.getName().equalsIgnoreCase("vd")) && (this.config.cu)) {
			if (sender.hasPermission("vd.command")) {
				if (args.length > 1) {
					
					if (args[0].equalsIgnoreCase("crear")) {
						if ((sender instanceof Player)) {
							int id = 0;
							try {
								id = Integer.parseInt(args[1]);
							} catch (Exception e) {
								s(sender, "La ID debe ser un numero.");
								return false;
							}
							if ((id < 0) && (id > 26)) {
								s(sender, "Por favor elige una ID de 0 a 26.");
								return false;
							}
							if (isArena(id)) {
								s(sender, "Esa ID ya esta en uso.");
								return false;
							}
							Player p = (Player)sender;
							this.am.createArena(p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), p.getLocation(), id + "_arena", id, 1, 10);
							s(p, "Arena numero" + id + " creada correctamente. Configurala con /vd configurar.");
						} else {
							s(sender, "Este comando solo lo puede ejecutar un jugador.");
						}
					}
					else if (args[0].equalsIgnoreCase("configurar")) {
						if (args.length < 3) {
							s(sender, "Uso: /vd configurar <id_de_la_arena> <configuracion> [argumentos]");
							return false;
						}
						if (getConfig().getConfigurationSection("arenas." + args[1]) == null) {
							s(sender, "ID de arena no encontrada.");
							return false;
						}
						int id = 0;
						try {
							id = Integer.parseInt(args[1]);
						} catch (Exception e) {
							s(sender, "La ID debe ser un numero.");
							return false;
						}
						
						args[2] = args[2].toLowerCase();
						if (checkArgs(args[2])) {
							if ((args[2].toLowerCase().startsWith("v")) || (args[2].toLowerCase().startsWith("z")) || (args[2].equalsIgnoreCase("ps")) || (args[2].equalsIgnoreCase("lobby"))) {
								if ((sender instanceof Player)) {
									Player p = (Player)sender;
									this.am.setArenaSetup(id, args[2], this.am.serializeLoc(p.getLocation()));
									reloadArena(id);
									s(sender, "Cambiada correctamente la configuracion de \"" + args[2] + "\" a \"" + this.am.serializeLoc(p.getLocation()) + "\".");
								} else {
									s(sender, "Este comando solo lo puede ejecutar un jugador.");
								}
							} else {
								if (args.length < 4) {
									s(sender, "Esta configuracion requiere argumentos.");
									return false;
								}
								if (checkArgsInt(args[2])) {
									try {
										int idas = Integer.parseInt(args[3]);
										this.am.setArenaSetup(id, args[2], Integer.valueOf(idas));
										reloadArena(id);
										s(sender, "Cambiada correctamente la configuracion de \"" + args[2] + "\" a \"" + idas + "\".");
										return false;
									} catch (Exception e) {
										s(sender, "La ID debe ser un numero.");
										return false;
									}
								}
								this.am.setArenaSetup(id, args[2], args[3]);
								reloadArena(id);
								s(sender, "Cambiada correctamente la configuracion de \"" + args[2] + "\" a \"" + args[3] + "\".");
							}
						}
						else s(sender, "Revisa tu configuracion.");
					}
				}
				else if (args.length == 1) {
					if (args[0].equalsIgnoreCase("crear")) {
						s(sender, "Crea tu arena con el comando: /vd crear <id_de_la_arena>");
						s(sender, "La ID de la arena debe ser de 0 a 26.");
						s(sender, "Despues configurala con: /vd configurar");
					} else if (args[0].equalsIgnoreCase("configurar")) {
						s(sender, "Configura tu arena con el comando: /vd configurar <id_de_la_arena> <configuracion> [argumentos]");
						s(sender, "Ejemplo: /vd configurar 1 name PrimeraArena");
						s(sender, "Configuraciones:");
						s(sender, "z1 ... z3 - configura los 3 puntos de spawn de zombies. Se configura en tu posicion actual.");
						s(sender, "v1 ... v3 - configura los 3 puntos de spawn de aldeanos. Se configura en tu posicion actual.");
						s(sender, "ps - configura el punto de spawn de los jugadores. Se configura en tu posicion actual.");
						s(sender, "lobby - configura el punto de spawn de los jugadores muertos. Debe de estar algo alejado. Yo recomiendo un sitio algo ;).");
						s(sender, "name - configura el nombre de la arena. Necesita el argumento: <nombre>");
						s(sender, "maxplayers - configura el numero maximo de jugadores. Necesita el argumento: <jugadores_maximo(solo numeros)>");
					} else {
						s(sender, "Prueba /vd.");
					}
				} else {
					s(sender, "Bienvenidos a Defiende la Villa.");
					s(sender, "Solo los jugadores con el permiso \"vd.command\" pueden usar este comando.");
					s(sender, "Comandos disponibles:");
					s(sender, "/vd crear - escribelo para mas informacion.");
					s(sender, "/vd configurar - escribelo para mas informacion.");
				}
			}
			else s(sender, this.config.get("no_perm"));
		}
		return false;
	} 

		
	void s(CommandSender s, String ss) {
		s.sendMessage(ChatColor.GRAY + "V" + ChatColor.RED + "D " + ChatColor.WHITE + ss);
	}

	public static void _log(String s) {
		Bukkit.getLogger().info("[" + Main.plugin.getDescription().getName() + "] " + s);
	}
	
	public static void _logD(String s) {
		if (Main.debug) {
			Bukkit.getLogger().info("[" + Main.plugin.getDescription().getName() + "] " + s);
		}
	}

	public static void _logE(String s) {
		Bukkit.getLogger().log(Level.SEVERE, "[" + plugin.getDescription().getName() + "] " + s);
	}

	public boolean isArena(int id) {
		for (Arena a : this.am.arenas)
			if (a.id == id)
				return true;
		return false;
	}

	//Este metodo pone metadatos al jugador a la hora de elegir kit!!!!
	public void setKit(Player jugador, String s) {
		jugador.setMetadata("vdkit", new FixedMetadataValue(this, s));
	}

	//Este metodo lee los metadatos del jugador para el kit
	public String getKit(Player pl) {
		return ((MetadataValue)pl.getMetadata("vdkit").get(0)).asString();
	}

	//Para actualizar los carteles
	public void updateSign(Arena a) {
		if (a.sign != null) {
			Sign s = (Sign)a.sign.getBlock().getState();
			String name = a.pav;
			if (name.length() > 16)
				name = name.substring(0, 16);
			s.setLine(0, ChatColor.DARK_RED + a.pav);
			s.setLine(2, ChatColor.GREEN + "" + a.jugadores.size() + ChatColor.GREEN + "/" + ChatColor.GREEN + a.maximoJugadores);
			if (a.puedeUnirse) {
				s.setLine(1,ChatColor.DARK_PURPLE +  this.config.get("sb_starting"));
				s.setLine(3,ChatColor.BLUE + this.config.get("sign"));
			} else {
				s.setLine(1,ChatColor.DARK_PURPLE + this.config.get("sb_wave").replace("$1", Integer.toString(a.oleada)));
				s.setLine(3,ChatColor.DARK_BLUE + this.config.get("sign_full"));
			}
			s.update();
		}
	}

	@EventHandler
	public void onChange(SignChangeEvent ev) {
		if (ev.getLine(0).equals("[vd]")) {
			int aId = 0;
			try {
				aId = Integer.parseInt(ev.getLine(1));
			} catch (Exception e) {
				s(ev.getPlayer(), "La ID no es un numero.");
				return;
			}
			if (isArena(aId)) {
				this.am.setArenaSetup(aId, "sign", this.am.serializeLoc(ev.getBlock().getLocation()));
				reloadArena(aId);
				s(ev.getPlayer(), "Configurado correctamente \"sign\" a \"" + this.am.serializeLoc(ev.getBlock().getLocation()) + "\".");
			} else {
				s(ev.getPlayer(), "No existe ninguna arena con esa ID.");
			}
		}
	}

	@EventHandler
	public void onD(EntityDeathEvent ev) {
		//Si se muere un aldeano...
		if (ev.getEntityType().equals(EntityType.VILLAGER)) {
			Villager v = (Villager)ev.getEntity();
			for (Arena a : this.am.arenas) {
				if (a.aldeanos.contains(v)) {
					a.aldeanos.remove(v);
					this.am.updateSc(a);
					if (a.esperandoSiguienteOleada)
						return;
					for (String s : a.jugadores)
						s(Bukkit.getPlayer(s), this.config.get("vill_death").replace("$1", Integer.toString(a.aldeanos.size())));
					this.am.checkVillagers(a);
				}
			}
		}

		//Si lo que se muere es un zombie
		if (ev.getEntityType().equals(EntityType.ZOMBIE)) {
			ev.getDrops().clear(); //no dropea nada
			ev.setDroppedExp(0); //Ni experiencia
			
			//Ahora añado que dropee una gema!
			ItemStack gema = new ItemStack(Material.EMERALD, 1);
			ev.getDrops().add(gema);
			
			Zombie z = (Zombie)ev.getEntity();
			for (Arena a : this.am.arenas)
				if (a.zombies.contains(z)) {
					a.zombies.remove(z);
					this.am.updateSc(a);
					if (a.esperandoSiguienteOleada)
						return;
					this.am.checkZombies(a);

					//Vamos a buscar el que lo mató para darle puntuación
					if ((ev.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent)) {
						EntityDamageByEntityEvent nev = (EntityDamageByEntityEvent)ev.getEntity().getLastDamageCause();
						if ((nev.getDamager() instanceof Player)) {
							Player p = (Player)nev.getDamager();
							//Resulta que si es VIP se le da más puntos que no siendo vip... Estudiar a ver que se hace
							if (p.hasPermission("vd.vip")) {
								int ran = new Random().nextInt(15) + 16;
								this.am.addScore(p, ran);
								s(p, this.config.get("kill").replace("$1", Integer.toString(ran)));
							} else {
								int ran = new Random().nextInt(15) + 1;
								this.am.addScore(p, ran);
								s(p, this.config.get("kill").replace("$1", Integer.toString(ran)));
							}
						}
					}
				}
		}
	}
	
	//Evento para al coger la gema te de los puntos
	@EventHandler
	public void cogerGema(PlayerPickupItemEvent ev){
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
					if (arena.jugadoresmuertos.contains(player.getName())) {
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

			 // Cncel the event (the player is gaming)
			 ev.setCancelled(true);
		 }
	}

	//Evento de si muere un jugador
	@EventHandler
	public void onDeath(PlayerDeathEvent ev) {
		if (this.am.isInGame(ev.getEntity())) {
			ev.getDrops().clear();
			ev.setDeathMessage(null);
			for (Arena a : this.am.arenas) {
				if (a.jugadores.contains(ev.getEntity().getName())) {
					for (Zombie z : a.zombies) {
						if (z.getTarget().equals(ev.getEntity())) {
							CraftZombie z2 = (CraftZombie) z;
							z2.getHandle().setGoalTarget(((CraftLivingEntity) a.aldeanos.get(0)).getHandle());
						}
					}
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	public void onKick(PlayerKickEvent ev)
	{
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)) {
			this.am.removePlayer(player);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST)
	public void onRespawn(PlayerRespawnEvent ev)
	{
		Player player = ev.getPlayer();
		
//		if (this.am.isInGame(ev.getPlayer())) {
//			for (Arena a : this.am.arenas) {
//				if (a.jugadores.contains(ev.getPlayer().getName())) {
//					if ((!a.esperandoSiguienteOleada) && (!a.noHaEmpezado)) {
//						//ev.setRespawnLocation(ev.getPlayer().getLocation());
//						ev.setRespawnLocation(a.lobby);
//						this.am.removePlayer(ev.getPlayer());
//					} else {
//						Player p = ev.getPlayer();
//						this.am.añadirKit(p);
//						ev.setRespawnLocation(a.ps);
//					}
//				}
//			}
//		}
		
		if (this.am.isInGame(player)) {
			// Search in which arena the user is playing
			for (Arena a : this.am.arenas) {
				if (a.jugadores.contains(player.getName())) {
					// Found the arena!
					if ((!a.esperandoSiguienteOleada) && (!a.noHaEmpezado) && (!a.jugadoresmuertos.contains(ev.getPlayer().getName()))) {
						_log("Poniendo al jugador en modo spectator");
						// If there aren't waiting for the next wave, the game has started and the player wasn't dead, put him in
						// spectator mode.
						a.jugadoresmuertos.add(player.getName());
						
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
							s(Bukkit.getPlayer(s), this.config.get("died").replace("$1", ev.getPlayer().getName()));
						}
						
						// Remove potion effects in the player
						for(PotionEffect pe : ev.getPlayer().getActivePotionEffects()){
							ev.getPlayer().removePotionEffect(pe.getType());
						}
						
						this.am.updateSc(a);
					}
					this.am.checkPlayers(a);
				}
			}
		}
	}

	//Evento de que sale el jugador
	@EventHandler(priority = EventPriority.NORMAL)
	public void onLeave(PlayerQuitEvent ev) {
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)) {
			this.am.removePlayer(player);
		}
	}

	//Evento de dropear objetos
	@EventHandler
	public void onDrop(PlayerDropItemEvent ev) {
		Player player = ev.getPlayer();
		
		if (this.am.isInGame(player)){
			ev.setCancelled(true);
		}
	}

	//Esto anula comandos salvo los que estén en la config de no_command_in_game
	@EventHandler
	public void onCommandPre(PlayerCommandPreprocessEvent ev) {
		if (this.am.isInGame(ev.getPlayer())) {
			boolean c = true;
			for (String com : this.allowedCommands) {
				if (ev.getMessage().toLowerCase().startsWith("/" + com)) {
					c = false;
				}
			}
			if (c)
				s(ev.getPlayer(), this.config.get("no_command_in_game"));
			ev.setCancelled(c);
		}
	}

	//Evento del target de los zombies
	@EventHandler
	public void onTarget(EntityTargetEvent ev) { //EntityTargetLivingEntityEvent ev) {
		if ((ev.getTarget() instanceof Player)) {
			Player pl = (Player)ev.getTarget();
			if (this.am.isInGame(pl)) {
				for (Arena a : this.am.arenas) {
					if ((a.jugadoresmuertos.contains(pl.getName())) || (a.noHaEmpezado) || (a.esperandoSiguienteOleada)) {
						ev.setCancelled(true);
					}
				}
			}
		}
	}

	//Evento para cancelar el hambre si está jugando
	@EventHandler
	public void onHunger(FoodLevelChangeEvent ev) {
		if ((ev.getEntity() instanceof Player)) {
			Player pl = (Player)ev.getEntity();
			if (this.am.isInGame(pl)) {
				for (Arena a : this.am.arenas) {
					if (a.jugadoresmuertos.contains(pl.getName()))
						ev.setCancelled(true);
				}
			}
		}
	}

	//Evento de daño a las entidades
	@EventHandler
	public void entityDamage(EntityDamageEvent ev) {
		if ((ev.getEntity() instanceof Player)) {
			Player pl = (Player)ev.getEntity();
			if (this.am.isInGame(pl)) {
				for (Arena a : this.am.arenas) {
					if ((a.jugadoresmuertos.contains(pl.getName())) || (a.noHaEmpezado)) {
						ev.setCancelled(true);
					}
				}
			}
			else if (ev.getCause().equals(EntityDamageEvent.DamageCause.VOID)) {
				ev.setCancelled(true);
				pl.teleport(pl.getWorld().getSpawnLocation());
			}
		}
		else if ((((ev.getEntity() instanceof Player)) || ((ev.getEntity() instanceof Villager))) && ((ev.getCause().equals(EntityDamageEvent.DamageCause.FIRE)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.FIRE_TICK)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.POISON)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.WITHER)) || (ev.getCause().equals(EntityDamageEvent.DamageCause.MAGIC)))) {
			ev.getEntity().setFireTicks(0);
			ev.setCancelled(true);
		}
	}

	// To avoid the zombies firing during sun time
	@EventHandler
	public void onEntityCombust(EntityCombustEvent event) {
		if ((event.getEntity() instanceof Zombie))
			// Only apply if the zombie is in an arena world
			if (this.am.isInGame((Zombie) event.getEntity())) {
				event.setCancelled(true);
		}
	}

	//Para evitar dañar a los aldeanos u a otros jugadores
	@EventHandler
	public void onDamage(EntityDamageByEntityEvent ev) {
		if ((ev.getDamager() instanceof Player)) {
			Player pl = (Player)ev.getDamager();
			if (this.am.isInGame(pl)) {
				if (((ev.getEntity() instanceof Villager)) || ((ev.getEntity() instanceof Player))) {
					ev.setCancelled(true);
				}
				for (Arena a : this.am.arenas)
					if (a.jugadoresmuertos.contains(pl.getName()))
						ev.setCancelled(true);
			}
		}
		else if (ev.getDamager() instanceof Arrow){ //Para evitar el daño por arco
			if (((ev.getEntity() instanceof Villager)) || ((ev.getEntity() instanceof Player))) {
				ev.setCancelled(true);
			}
        }
	}

	//Para que no puedan comerciar con los aldeanos
	@EventHandler
	public void onInterEnt(PlayerInteractEntityEvent ev) {
		if (((ev.getRightClicked() instanceof Villager)) && (this.am.isInGame(ev.getPlayer())))
			ev.setCancelled(true);
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent ev) {
		//Object localObject1;
		
		Player jugador = ev.getPlayer();
		
		if (this.am.isInGame(ev.getPlayer()))
		{
			Iterator<Arena> localObject1 = this.am.arenas.iterator();
			while (localObject1.hasNext()) {
				Arena a = localObject1.next();
				if (a.jugadoresmuertos.contains(ev.getPlayer().getName())) {
					ev.setCancelled(true);
				}
			}
		}
		
		Boolean clickSign = false;

		if (ev.getClickedBlock() != null) {
			Iterator<Arena> localObject2 = this.am.arenas.iterator();
			while (localObject2.hasNext()) {
				Arena a = localObject2.next();
				if ((a.sign != null) && (a.sign.equals(ev.getClickedBlock().getLocation()))) {
					this.am.addPlayer(ev.getPlayer(), a.id);
					clickSign = true;
					ev.setCancelled(true);
				}
			}
		}

		if ((jugador.getItemInHand().equals(this.object_kits_book) && (!clickSign))) {
			this.selectKit.show(jugador);
			ev.setCancelled(true);
		}
		
		if ((jugador.getItemInHand().equals(this.emerald_item) && (!clickSign))) {
			this.gemShop.show(jugador);
			ev.setCancelled(true);
		}
	}
	
	//Evento los libros!!!!
	@EventHandler
	public void onInventoryClickEvent (InventoryClickEvent event) {
		//Bloqueo los items ligados en todos los contenedores
		Player jugador = (Player) event.getWhoClicked(); //Lo parseamos
	
		if ((event.getInventory().getType() == InventoryType.CHEST) && (event.getInventory().getName().equals("Selecciona tu kit"))){
			
			int slot = event.getRawSlot();
			
			if ((slot > 21) || (slot == -999)) {
				event.setCancelled(true);
				return;
			}
			
			if (event.getCurrentItem().getItemMeta() == null) return;
			
			//Metodo para evitar que cambien de kit en mitad de una partida
			for (Arena a: this.am.arenas) {
				if (a.jugadores.contains(jugador.getName())){
					if (jugador.hasPermission("vd.vip")) {
						//si el jugador es vip
						if (!a.cambiarKit) {
							s(jugador, "Solo puedes cambiar de kit en el descanso entre oleadas.");
							event.setCancelled(true);
							jugador.closeInventory();
							return;
						}
					}
					else {
						if (!a.noHaEmpezado) {
							event.setCancelled(true);
							jugador.closeInventory();
							return;
						}
					}
				}
			}

			if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Tanque")) {
				Main.this.setKit(jugador, "tanque");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Piromano")) {
				Main.this.setKit(jugador, "piromano");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Bruja")) {
				Main.this.setKit(jugador, "bruja");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Hardcore")) {
				Main.this.setKit(jugador, "hardcore");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Arquero")) {
				Main.this.setKit(jugador, "arquero");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Cadete")) {
				Main.this.setKit(jugador, "cadete");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Peleador")) {
				Main.this.setKit(jugador, "peleador");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Congelado")) {
				Main.this.setKit(jugador, "congelado");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Parkour")) {
				Main.this.setKit(jugador, "parkour");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Gordito")) {
				Main.this.setKit(jugador, "gordito");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Defensa")) {
				Main.this.setKit(jugador, "defensa");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Corredor")) {
				Main.this.setKit(jugador, "corredor");
                Main.this.s(jugador, Main.this.config.get("kit_selected"));
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Espadachin")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "espadachin");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Pacifico")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "pacifico");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Protegido")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "protegido");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Experto")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "experto");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Sabueso")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "sabueso");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Conejo")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "conejo");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Enfermero")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "enfermero");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Legolas")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "legolas");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("OP")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "op");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			} else if (event.getCurrentItem().getItemMeta().getDisplayName().contains("Dorado")) {
				if (jugador.hasPermission("vd.vip")) {
					Main.this.setKit(jugador, "dorado");
					Main.this.s(jugador, Main.this.config.get("kit_selected"));
				} else {
					Main.this.s(jugador, Main.this.config.get("kit_vip"));
				}
				event.setCancelled(true);
				jugador.closeInventory();
			}
		}
		else if ((event.getInventory().getType() == InventoryType.CHEST) && (event.getInventory().getName().equals("Compra objetos"))) {
			int slot = event.getRawSlot();
			
			if ((slot > 31) || (slot == -999)) {
				event.setCancelled(true);
				return;
			}
			
			if (event.getCurrentItem().getItemMeta() == null){
				event.setCancelled(true);
				return; //Si es aire, vuelve...
			}
			
			boolean jugando = false;
			
			for (Arena a: this.am.arenas) {
				if (a.jugadores.contains(jugador.getName())){
					jugando = true;
					break;
				}
			}
			
			if (!jugando){
				event.setCancelled(true);
				jugador.closeInventory();
				return;
			}

			Integer i = 0;
			
			List<ItemStack> copia = this.gemShop.objectList;
			Iterator<ItemStack> itr = copia.iterator();
			while (itr.hasNext()) {
				if (i == slot) { //Si coinciden es mi objeto
					ItemStack objeto = itr.next();
					ItemMeta meta = (ItemMeta) objeto.getItemMeta();
					String valorString = meta.getLore().toString().replace(" puntos.", "").replace("[", "").replace("]", "");
					Integer valor = Integer.parseInt(valorString);
					Integer puntos = this.am.getScore(jugador);
					if (valor > puntos) {
						s(jugador, "No tienes puntos suficientes");
						event.setCancelled(true);
						jugador.closeInventory();
						break;
					}
					else {
						this.am.setScore(jugador, puntos-valor);
						jugador.getInventory().addItem(objeto);
						event.setCancelled(true);
						jugador.closeInventory();
						break;
					}
				}
				else {
					itr.next();
					i++;
				}
			}
		}
		
		else { //Otros inventarios
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