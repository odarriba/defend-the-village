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

	public Arena getArena(int i) {
		for (Arena a : this.arenas) {
			if (a.getId() == i) {
				return a;
			}
		}
		return null;
	}

	public ArrayList<Player> getPlayersInArena(Arena a) {
		ArrayList<Player> pls = new ArrayList<Player>();
		for (String s : a.jugadores) {
			pls.add(Bukkit.getPlayer(s));
		}
		return pls;
	}

	public void addPlayer(final Player p, int i) {
		if (!this.plugin.config.cu)
			return;
		if ((!p.isOnline()) || (p.isDead()))
			return;
		final Arena a = getArena(i);
		if (a == null) {
			p.sendMessage("Invalid arena!");
			return;
		}

		if (!a.puedeUnirse) {
			if (!p.hasPermission("vd.vip")) {
				this.plugin.s(p, this.plugin.config.get("arena_started"));
				this.plugin.s(p, this.plugin.config.get("compra_vip"));
				return;
			}
		}

		if (a.getPlayers().contains(p.getName())) {
			return;
		}
		
		if (a.getPlayers().size() >= a.maximoJugadores) {
			this.plugin.s(p, this.plugin.config.get("arena_full"));
			return;
		}

		a.getPlayers().add(p.getName());
		this.inv.put(p.getName(), p.getInventory().getContents());
		this.armor.put(p.getName(), p.getInventory().getArmorContents());

		p.getInventory().setArmorContents(null);
		p.getInventory().clear();
		p.setFoodLevel(20);
		p.setHealth(20);

		this.locs.put(p.getName(), p.getLocation());
		/*Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
			public void run() {
				p.teleport(a.ps);
			}
		}, 10L);*/
		p.teleport(a.ps);

		p.getInventory().addItem(new ItemStack[] { this.plugin.libro_kits_objeto });
		p.updateInventory();

		createScoreboard(p, a);

		for (Player pl : getPlayersInArena(a)) {
			if (a.puedeUnirse) { //Para que no salga a los que se acaban de unir
				this.plugin.s(pl, this.plugin.config.get("waiting_for_players").replace("$1", Integer.toString(a.getPlayers().size())).replace("$2", Integer.toString(a.maximoJugadores)));
			}
			this.plugin.s(pl, "El jugador " + p.getName() + " se ha unido a la arena.");
		}

		if ((!a.noHaEmpezado) && (a.getPlayers().size() >= a.maximoJugadores)) {
			a.noHaEmpezado = true;
			a.contador = this.plugin.getConfig().getInt("config.starting_time");
			for (Player pl : getPlayersInArena(a)) {
				this.plugin.s(pl, this.plugin.config.get("starting_in").replace("$1", Integer.toString(a.contador)));
			}
		}

		if ((!a.noHaEmpezado) && (a.getPlayers().size() == 3)) {
		//if ((!a.noHaEmpezado) && (a.getPlayers().size() == 1)) {
			a.noHaEmpezado = true;
			a.contador = 50;
			for (Player pl : getPlayersInArena(a)) {
				this.plugin.s(pl, this.plugin.config.get("starting_in").replace("$1", Integer.toString(a.contador)));
				p.setHealth(20);
				p.setFoodLevel(20);
			}
		}

		this.plugin.updateSign(a);

		Main._log("Added player " + p.getName() + " to arena " + a.getId() + " (" + a.pav + ").");
		
		updateSc(a);
	}

	public void removePlayer(final Player p) {
		//Si está muerto, eso lo que hace es esperar al ForceRespawn (o eso creo...)
		if (p.isDead())
			Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
				public void run() {
					ArenaManager.this.removePlayerA(p);
				}
			}, 1L); // 1 tick de delay
		else
			removePlayerA(p); //sino directamente lo quito
	}

	public void removePlayerA(final Player p) {
		if (!this.plugin.config.cu)
			return;
		if (!p.isOnline()) //Tiene que estar conectado
			return;
		Arena a = null;
		for (Arena arena : this.arenas) {
			if (arena.getPlayers().contains(p.getName())) {
				a = arena;
			}
		} //FIN FOR

		//El ultimo jugador no tiene ninguna arena disponible, asi que... como hago??!!!
		if ((a == null) || (!a.getPlayers().contains(p.getName())))
		{
			Main._logE("Algo ha fallado con el jugador " + p.getName());
			p.kickPlayer("Algo ha fallado...\nPor razones de seguridad te expulso.\nInforma a un admin.");
			return;
		}

		a.getPlayers().remove(p.getName());
		if (a.jugadoresmuertos.contains(p.getName())) {
			a.jugadoresmuertos.remove(p.getName());
		}

		p.getInventory().clear();
		p.getInventory().setArmorContents(null);

		p.getInventory().setContents((ItemStack[])this.inv.get(p.getName()));
		p.getInventory().setArmorContents((ItemStack[])this.armor.get(p.getName()));

		this.inv.remove(p.getName());
		this.armor.remove(p.getName());

		final Location loc = (Location)this.locs.get(p.getName());
		Bukkit.getScheduler().scheduleSyncDelayedTask(this.plugin, new Runnable() {
			public void run() {
				p.teleport(loc);
			}
		}, 10L);
		//No entiendo por qué un delay
		//p.teleport(loc);

		this.locs.remove(p.getName());

		p.setFireTicks(0);
		
		for(PotionEffect pe : p.getActivePotionEffects()){
			p.removePotionEffect(pe.getType());
		}

		removeScoreboard(p);
		
		p.setAllowFlight(false);
		p.setFlying(false);
		
		for (Player pl : Bukkit.getOnlinePlayers()) {
			if (p != pl) {
				pl.showPlayer(p);
			}
		}

		for (Player pl : getPlayersInArena(a)) {
			this.plugin.s(pl, this.plugin.config.get("left_arena").replace("$1", p.getName()));
		}

		this.plugin.updateSign(a);

		Main._log("Quitado jugador " + p.getName() + " de la arena " + a.getId() + " (" + a.pav + ").");

		if (a.jugadores.size() == 0)
			this.plugin.reloadArena(a.id);
	} //FIN removePlayerA

	public Arena createArena(Location z1, Location z2, Location z3, Location v1, Location v2, Location v3, Location ps, Location lobby, String pav, int id, int icon, int mp)
	{
		if (!this.plugin.config.cu)
			return null;
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

	public void setArenaSetup(int id, String setup, Object var) {
		if (!this.plugin.config.cu)
			return;
		this.plugin.getConfig().set("arenas." + id + "." + setup, var);
		this.plugin.saveConfig();
		Main._log("Configurado " + setup + "=" + var + " para la arena " + id + ".");
	}

	public boolean isInGame(Player p) {
		for (Arena a : this.arenas) {
			if (a.getPlayers().contains(p.getName()))
				return true;
		}
		return false;
	}
	
	public boolean isInGame(Zombie z) {
		for (Arena a : this.arenas) {
			if (a.getZombies().contains(z))
				return true;
		}
		return false;
	}

	public String serializeLoc(Location l) {
		return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
	}

	public Location deserializeLoc(String s) {
		String[] st = s.split(",");
		return new Location(Bukkit.getWorld(st[0]), Integer.parseInt(st[1]), Integer.parseInt(st[2]), Integer.parseInt(st[3]));
	}

	public void start(Arena a) {
		if (a.oleada > 0) return;
		Main._log("Comenzando arena " + a.getId() + " (" + a.pav + ").");
		a.puedeUnirse = false;
		a.cambiarKit = false;
		a.oleada = 1;
		a.vil = 3.0F;
		a.check = true;

		this.plugin.updateSign(a);
		for (String s : a.jugadores) {
			if (Bukkit.getPlayer(s) == null) {
				a.jugadores.remove(s);
			} else {
				this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("starting").replace("$1", Integer.toString(a.oleada)));
				if (!Bukkit.getPlayer(s).getInventory().contains(this.plugin.esmeralda_item)) {
					Bukkit.getPlayer(s).getInventory().addItem(new ItemStack[] { this.plugin.esmeralda_item });
				}
				if (Bukkit.getPlayer(s).getInventory().contains(this.plugin.libro_kits_objeto)) {
					Bukkit.getPlayer(s).getInventory().removeItem(new ItemStack[] { this.plugin.libro_kits_objeto });
				}
				Player p = Bukkit.getPlayer(s);
				if (p.getScoreboard().getObjective(DisplaySlot.SIDEBAR) == null)
					createScoreboard(p, a);
				p.getScoreboard().getObjective(DisplaySlot.SIDEBAR).setDisplayName(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + this.plugin.config.get("sb_wave").replace("$1", "1") + ChatColor.DARK_GRAY + " -");
				p.setHealth(20);
				p.setFoodLevel(20);
				
				/** AQUI SE EMPIEZA A AÑADIR LOS KITS **/
				
				añadirKit(p);
				
			} //FIN ELSE
		} //FIN for jugadores

		for (int i = 0; i < a.vil; i++) {
			int r = new Random().nextInt(3);
			switch (r) {
			case 0:
				a.aldeanos.add((Villager)a.v1.getWorld().spawnEntity(a.v1, EntityType.VILLAGER));
				break;
			case 1:
				a.aldeanos.add((Villager)a.v2.getWorld().spawnEntity(a.v2, EntityType.VILLAGER));
				break;
			case 2:
				a.aldeanos.add((Villager)a.v3.getWorld().spawnEntity(a.v3, EntityType.VILLAGER));
			}
		}

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
			v.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 1));
		}
		
		updateSc(a);
	} //FIN start(Arena)

	public void nextwave(Arena a) {
		if (a.oleada == 0) return;
		a.oleada += 1;
		Main._log("Siguiente oleada (" + a.oleada + ") en la arena " + a.getId() + "(" + a.pav + ").");
		a.cambiarKit = false;
		for (String s : a.jugadores) {
			Player jugador = Bukkit.getPlayer(s);
			if (jugador == null) {
				a.jugadores.remove(s);
			} else {
				this.plugin.s(jugador, this.plugin.config.get("starting").replace("$1", Integer.toString(a.oleada)));
				if (!jugador.getInventory().contains(this.plugin.esmeralda_item)) {
					if (this.plugin.getKit(jugador).equals("hardcore")) {
						//No le doy item, porque está muerto
					}
					else {
						jugador.getInventory().addItem(new ItemStack[] { this.plugin.esmeralda_item });
					}
				}
				if (jugador.getInventory().contains(this.plugin.libro_kits_objeto)) {
					jugador.getInventory().removeItem(new ItemStack[] { this.plugin.libro_kits_objeto });
					añadirKit(jugador);
				}
				if (jugador.getScoreboard().getObjective(DisplaySlot.SIDEBAR) == null)
					createScoreboard(jugador, a);
				jugador.getScoreboard().getObjective(DisplaySlot.SIDEBAR).setDisplayName(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + this.plugin.config.get("sb_wave").replace("$1", new StringBuilder(String.valueOf(a.oleada)).toString()) + ChatColor.DARK_GRAY + " -");
			}
		}

		for (Zombie z : a.zombies)
			z.damage(9000.0D);
		for (Villager v : a.aldeanos)
			v.damage(9000.0D);
		
		//Cada oleada multiplo de 5 se suma 1 aldeano y un boss!!!
		if (a.oleada % 5 == 0) {
			a.vil += 4.0F;
			a.b += 1.0F;
		}
		
		//Esto es para ir añadiendo zombies a cada ronda
		if (a.oleada <= 10) {
			a.zomb = ((float)(a.zomb + 1.3D));
		} else if ((a.oleada > 10) && (a.oleada <= 20)){
			a.zomb = ((float)(a.zomb + 2.7D)); //el doble
		} else if ((a.oleada > 20) && (a.oleada <= 30)){
			a.zomb = ((float)(a.zomb + 4.1D)); //triple
		} else if (a.oleada > 30) {
			a.zomb = ((float)(a.zomb + 5.5D)); //cuadruple, LOL xD
		}
		
		this.plugin.updateSign(a);
		
		for (int i = 0; i < a.vil; i++) {
			int r = new Random().nextInt(3);
			switch (r) {
				case 0:
					a.aldeanos.add((Villager)a.v1.getWorld().spawnEntity(a.v1, EntityType.VILLAGER));
					break;
				case 1:
					a.aldeanos.add((Villager)a.v2.getWorld().spawnEntity(a.v2, EntityType.VILLAGER));
					break;
				case 2:
					a.aldeanos.add((Villager)a.v3.getWorld().spawnEntity(a.v3, EntityType.VILLAGER));
			}
		}

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

			if (new Random().nextInt(4) == 0)
				v.setBaby(true);
			else
				v.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 1));
			if ((a.oleada > 4) && (a.oleada < 17) && (new Random().nextInt(2) == 0))
				v.getEquipment().setItemInHand(new ItemStack(Material.WOOD_SWORD));
			if ((a.oleada > 6) && (a.oleada < 19) && (new Random().nextInt(2) == 0))
				v.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
			if ((a.oleada > 8) && (a.oleada < 21) && (new Random().nextInt(2) == 0))
				v.getEquipment().setHelmet(new ItemStack(Material.LEATHER_HELMET));
			if ((a.oleada > 10) && (a.oleada < 23) && (new Random().nextInt(2) == 0))
				v.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
			if ((a.oleada > 14) && (a.oleada < 25) && (new Random().nextInt(2) == 0))
				v.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
			if ((a.oleada > 16) && (a.oleada < 27) && (new Random().nextInt(2) == 0))
				v.getEquipment().setItemInHand(new ItemStack(Material.IRON_SWORD));
			if ((a.oleada > 18) && (a.oleada < 29) && (new Random().nextInt(2) == 0))
				v.getEquipment().setBoots(new ItemStack(Material.IRON_BOOTS));
			if ((a.oleada > 20) && (a.oleada < 31) && (new Random().nextInt(2) == 0))
				v.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
			if ((a.oleada > 22) && (a.oleada < 33) && (new Random().nextInt(2) == 0))
				v.getEquipment().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
			if ((a.oleada > 24) && (a.oleada < 35) && (new Random().nextInt(2) == 0))
				v.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
			if ((a.oleada > 26) && (a.oleada < 37) && (new Random().nextInt(2) == 0))
				v.getEquipment().setItemInHand(new ItemStack(Material.DIAMOND_SWORD));
			if ((a.oleada > 28) && (a.oleada < 39) && (new Random().nextInt(2) == 0))
				v.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
			if ((a.oleada > 30) && (a.oleada < 41) && (new Random().nextInt(2) == 0))
				v.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
			if ((a.oleada > 32) && (a.oleada < 43) && (new Random().nextInt(2) == 0))
				v.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
			if ((a.oleada > 34) && (new Random().nextInt(3) == 0))
				v.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
			
			//Potenciemos los zombies!
			if ((a.oleada > 10) && (a.oleada <= 20)){
				v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 1));
			} else if ((a.oleada > 20) && (a.oleada <= 30)){
				v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 2));
			} else if (a.oleada > 30) {
				v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 3));
			}

		}
		
		if (a.oleada % 5 == 0) { //Bosses
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
						a.zombies.add(v = (Zombie)a.z3.getWorld().spawnEntity(a.z3, EntityType.SKELETON)); //COMO HAGO QUE LOS LOBOS SEAN MALOS
				}
	
				v.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999999, 3));
				v.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 999999999, (a.oleada-2)*2));
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
				
				//Potenciemos los zombies!
				if ((a.oleada > 10) && (a.oleada <= 20)){
					v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 1));
				} else if ((a.oleada > 20) && (a.oleada <= 30)){
					v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 2));
				} else if (a.oleada > 30) {
					v.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 999999999, 3));
				}
				
			}
			for (String s : a.jugadores) {
				Player jugador = Bukkit.getPlayer(s);
				if (a.oleada == 5) {
					this.plugin.s(jugador, ChatColor.RED + "Un jefe de las hordas de zombies ha hecho acto de presencia... Y está muy muy enfadado...");
				}
				else {
					this.plugin.s(jugador, ChatColor.RED + "Varios jefes de las hordas de zombies han hecho acto de presencia... Y están muy muy enfadados...");
				}
			}
			
			
		}
		
		updateSc(a);
	} //Fin siguienteOleada

	public void checkVillagers(Arena a) {
		Main._log("Comprobando aldeanos en la arena " + a.getId() + "(" + a.pav + ").");
		if ((a.aldeanos.size() == 0) && (a.check)) {
			Main._log("0 encontrados!");
			for (String s : a.jugadores) {
				this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("z_win"));
			}
			this.plugin.reloadArena(a.id);
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	/*public void licencia() {
	
		//Variables
		String respuesta = "";
	
		try {
			respuesta = this.enviarIP();
		} catch (Exception e1) {
			Bukkit.getServer().getPluginManager().disablePlugin(this.plugin);
			Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "stop");
			return;
		}
		if (!respuesta.equals("200")) {
			Main._logE("Iniciando plugin");
		}
		else {
			Bukkit.getServer().getPluginManager().disablePlugin(this.plugin);
			Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), "stop");
			return;
		}
	} 
	
	//METODOS PARA LA LICENCIA
	private String enviarIP() throws Exception {
		
		String USER_AGENT = "LicenciaVD";
		 
		String url = "http://sv.nexuscraft.es/DLV/Privados/licencia.php?ip=" + URLEncoder.encode(Bukkit.getServer().getIp().toString(), "UTF-8");
 
		URL obj = new URL(url);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
 
		// optional default is GET
		con.setRequestMethod("GET");
 
		//add request header
		con.setRequestProperty("User-Agent", USER_AGENT);
 
		int responseCode = con.getResponseCode();
		//Main._log("Codigo respuesta : " + responseCode);
 
		BufferedReader in = new BufferedReader(
		        new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
 
		//print result
		//System.out.println(response.toString());
		
		return response.toString();
 
	}
	

	
	*/
	//FIn de licencia

	public void checkZombies(Arena a) {
		Main._log("Comprobando zombies en la arena " + a.getId() + "(" + a.pav + ").");
		if ((a.zombies.size() == 0) && (a.check)) {
			Main._log("0 encontrados! Esperando a la siguiente oleada, reviviendo jugadores...");
			a.esperandoSiguienteOleada = true;
			a.contador = 10;
			a.cambiarKit = true;
			int vilag = a.aldeanos.size();
			
			for (Villager v : a.aldeanos) {
				v.damage(99999.0D);
			}
			
			for (String s : a.jugadores) {
				if (!a.noHaEmpezado) {
					this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("v_win").replace("$1", Integer.toString(vilag * 3)).replace("$2", Integer.toString(vilag)));
					addScore(Bukkit.getPlayer(s), vilag * 3);
				}
			}
			
			for (String s : a.jugadoresmuertos) {
				Player p = Bukkit.getPlayer(s);
				if (this.plugin.getKit(p).equals("hardcore")) {
					//No le voy a resucitar, solo tiene 1 vida por juego
				}
				else
				{
					p.setFlying(false);
					p.setAllowFlight(false);

					for (Player pl : Bukkit.getOnlinePlayers()) {
						if (p != pl) {
							pl.showPlayer(p);
						}
					}

					p.damage(99999.0D);
					a.jugadoresmuertos.remove(s);
				}
			}
		}
	} //FIN checkZombies

	public void checkPlayers(Arena a) {
		Main._log("Comprobando jugadores en la arena " + a.getId() + "(" + a.pav + ").");
		if ((a.jugadores.size() <= a.jugadoresmuertos.size()) && (a.check)) {
			Main._log("Todos los jugadores han muerto!");
			for (String s : a.jugadores) {
				this.plugin.s(Bukkit.getPlayer(s), this.plugin.config.get("p_dead"));
			}
			this.plugin.reloadArena(a.id);
		}
	}

	public void addScore(Player pl, int i) {
		pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).setScore(getScore(pl) + i);
	}

	public void removeScore(Player pl, int i) {
		pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).setScore(getScore(pl) - i);
	}

	public void setScore(Player pl, int i) {
		pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).setScore(i);
	}

	public int getScore(Player pl) {
		return pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME)).getScore();
	}

	public void updateSc(Arena a) {
		for (String s : a.getPlayers()) {
			Objective o = Bukkit.getPlayer(s).getScoreboard().getObjective(DisplaySlot.SIDEBAR);
			o.getScore(Bukkit.getOfflinePlayer(this.ALIVE_NAME)).setScore(a.getPlayers().size() - a.jugadoresmuertos.size());
			o.getScore(Bukkit.getOfflinePlayer(this.DEAD_NAME)).setScore(a.jugadoresmuertos.size());
			o.getScore(Bukkit.getOfflinePlayer(this.VILLS_NAME)).setScore(a.aldeanos.size());
			o.getScore(Bukkit.getOfflinePlayer(this.ZOMBS_NAME)).setScore(a.zombies.size());
		}
	}

	public void createScoreboard(Player pl, Arena a) {
		if (!pl.isOnline()) {
			return;
		}
		Objective objective = Bukkit.getScoreboardManager().getNewScoreboard().registerNewObjective("VillageDef", "dummy");
		objective.setDisplayName(ChatColor.DARK_GRAY + "- " + ChatColor.GREEN + this.plugin.config.get("sb_starting") + ChatColor.DARK_GRAY + " -");
		objective.setDisplaySlot(DisplaySlot.SIDEBAR);
		pl.setScoreboard(objective.getScoreboard());

		Score vers = objective.getScore(Bukkit.getOfflinePlayer(this.POINTS_NAME));
		vers.setScore(1);

		vers = objective.getScore(Bukkit.getOfflinePlayer(this.ALIVE_NAME));
		vers.setScore(a.getPlayers().size() - a.jugadoresmuertos.size());

		vers = objective.getScore(Bukkit.getOfflinePlayer(this.DEAD_NAME));
		vers.setScore(a.jugadoresmuertos.size());

		vers = objective.getScore(Bukkit.getOfflinePlayer(this.VILLS_NAME));
		vers.setScore(a.aldeanos.size());

		vers = objective.getScore(Bukkit.getOfflinePlayer(this.ZOMBS_NAME));
		vers.setScore(a.zombies.size());
	}

	public void removeScoreboard(Player pl) {
		if ((pl.getScoreboard() != null) && (pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR) != null))
			pl.getScoreboard().getObjective(DisplaySlot.SIDEBAR).unregister();
	}
	
	public void añadirKit(Player p) {
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
