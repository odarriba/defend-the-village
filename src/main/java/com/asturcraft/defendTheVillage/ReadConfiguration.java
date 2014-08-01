package com.asturcraft.defendTheVillage;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public class ReadConfiguration{
	Plugin p;
	FileConfiguration c;
	public boolean cu = true;

	public ReadConfiguration(Plugin pl) {
		this.p = pl;
		this.c = this.p.getConfig();
	}

	public String get(String s) {
		String r = (String)this.c.get("config.lang." + s);
		if (r == null) {
			Main._logE("Error - String not found: config.lang." + s + ". Remove the configuration file and restar the server..");
			return ChatColor.RED + "ERROR: Please, contact an Administrator.";
		}
	return r;
	}

	public void cu()
	{
		this.cu = true;
	}
	
}