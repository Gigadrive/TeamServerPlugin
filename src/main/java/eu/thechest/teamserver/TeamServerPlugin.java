package eu.thechest.teamserver;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.achievement.Achievement;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.server.GameState;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.server.ServerUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by zeryt on 16.04.2017.
 */
public class TeamServerPlugin extends JavaPlugin {
    private static TeamServerPlugin instance;
    public static ArrayList<BindingPlayer> BINDING_PLAYERS = new ArrayList<BindingPlayer>();
    public static boolean MAY_SAVE_WORLD = true;
    public static ArrayList<Map> LOADED_MAPS = new ArrayList<Map>();

    public void onEnable(){
        if(!ServerUtil.getServerName().equalsIgnoreCase("TeamServer-1")){
            ChestAPI.stopServer();
            return;
        }

        instance = this;

        ServerSettingsManager.updateGameState(GameState.JOINABLE);
        ServerSettingsManager.KILL_EFFECTS = false;
        ServerSettingsManager.ARROW_TRAILS = false;
        ServerSettingsManager.PROTECT_ARMORSTANDS = false;
        ServerSettingsManager.PROTECT_ITEM_FRAMES = false;
        ServerSettingsManager.ENABLE_NICK = false;
        ServerSettingsManager.ADJUST_CHAT_FORMAT = false;
        ServerSettingsManager.SHOW_FAME_TITLE_ABOVE_HEAD = false;
        ServerSettingsManager.MAX_PLAYERS = getServer().getMaxPlayers();
        ServerSettingsManager.VIP_JOIN = false;
        ServerSettingsManager.UPDATE_TAB_NAME_WITH_SCOREBOARD = true;
        ServerSettingsManager.ALLOW_MULITPLE_MAPS = false;

        MainExecutor exec = new MainExecutor();
        getCommand("bindreward").setExecutor(exec);
        getCommand("prepareworld").setExecutor(exec);
        getCommand("saveworld").setExecutor(exec);
        getCommand("loadworld").setExecutor(exec);

        List<String> worlds = getConfig().getStringList("worlds");
        if(worlds != null){
            for(String s : worlds){
                World w = Bukkit.getWorld(s);
                if(w == null){
                    WorldCreator wc = new WorldCreator(s);
                    wc.createWorld();
                }
            }
        }

        getServer().getPluginManager().registerEvents(new MainListener(), this);
    }

    public void onDisable(){
        for(Map map : LOADED_MAPS){
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mvremove " + map.getOriginalWorldName());
        }
    }

    public static TeamServerPlugin getInstance(){
        return instance;
    }

    public boolean isBinding(Player p){
        return toBindingPlayer(p) != null;
    }

    public void setBinding(Player p, Achievement a, int coins){
        if(!isBinding(p)){
            BindingPlayer b = new BindingPlayer(p,a,coins);

            BINDING_PLAYERS.add(b);
        }
    }

    public BindingPlayer toBindingPlayer(Player p){
        for(BindingPlayer b : BINDING_PLAYERS){
            if(b.p.getName().equals(p.getName())) return b;
        }

        return null;
    }

    public void cancelBinding(Player p){
        if(isBinding(p)){
            BindingPlayer b = toBindingPlayer(p);

            BINDING_PLAYERS.remove(b);
        }
    }
}
