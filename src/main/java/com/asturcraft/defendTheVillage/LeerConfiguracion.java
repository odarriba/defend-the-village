package com.asturcraft.defendTheVillage;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public class LeerConfiguracion{
	Plugin p;
	FileConfiguration c;
	public boolean cu = true;

	public LeerConfiguracion(Plugin pl) {
		this.p = pl;
		this.c = this.p.getConfig();
	}

	public String get(String s) {
		String r = (String)this.c.get("config.lang." + s);
		if (r == null) {
			Main._logE("Error. No se ha encontrado el string: config.lang." + s + " . Borra el fichero de configuración y reinicia el servidor.");
			return ChatColor.RED + "ERROR: Verifica la consola.";
		}
	return r;
	}

	public void cu()
	{
		this.cu = true;
	}
	
}