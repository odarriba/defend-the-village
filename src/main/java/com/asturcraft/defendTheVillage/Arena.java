package com.asturcraft.defendTheVillage;

import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Location;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;

public class Arena
{
	public Location z1;
	public Location z2;
	public Location z3;
	public Location v1;
	public Location v2;
	public Location v3;
	public Location ps;
	public Location lobby;
	public Location sign = null;
	public int id;
	public int oleada = 0;
	public int maximoJugadores = 0;
	public int contador = 60;
	public float vil = 3.0F;
	public float zomb = 2.0F;
	public float b = 0.0F;
	public boolean esperandoSiguienteOleada = false;
	public boolean noHaEmpezado = false;
	public boolean check = false;
	public boolean puedeUnirse = true;
	public boolean cambiarKit = true;
	public CopyOnWriteArrayList<String> jugadores = new CopyOnWriteArrayList();
	public CopyOnWriteArrayList<String> jugadoresmuertos = new CopyOnWriteArrayList();
	public CopyOnWriteArrayList<Villager> aldeanos = new CopyOnWriteArrayList();
	public CopyOnWriteArrayList<Zombie> zombies = new CopyOnWriteArrayList();
	public CopyOnWriteArrayList<Skeleton> esqueletos = new CopyOnWriteArrayList();
	public String pav;

	public Arena(Location z1, Location z2, Location z3, Location v1, Location v2, Location v3, Location ps, Location lobby, String pav, int id, int maxPlayers)
	{
		this.z1 = z1;
		this.z2 = z2;
		this.z3 = z3;
		this.v1 = v1;
		this.v2 = v2;
		this.v3 = v3;
		this.id = id;
		this.pav = pav;
		this.ps = ps;
		this.lobby = lobby;
		this.id = id;
		this.maximoJugadores = maxPlayers;
	}

	public String toString() {
		return this.pav;
	}

	public int getId() {
		return this.id;
	}

	public CopyOnWriteArrayList<String> getPlayers() {
		return this.jugadores;
	}
}