package eu.thechest.teamserver;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.achievement.Achievement;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.StringUtils;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import net.lingala.zip4j.util.Zip4jUtil;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Blob;
import java.sql.PreparedStatement;

/**
 * Created by zeryt on 17.04.2017.
 */
public class MainExecutor implements CommandExecutor {
    private void saveLobbyTo(Player p, World currentWorld, String path) throws Exception {
        ChestUser u = ChestUser.getUser(p);
        File file = new File(path);
        if(file.exists() || file.isDirectory()){
            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Found old file - deleting.."));
            FileUtils.deleteDirectory(file);
        } else {
            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Old file not found - skipping delete step."));
        }

        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Copying world to lobby directory.."));
        String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? null : "/";
        FileUtils.copyDirectory(new File(Bukkit.getWorldContainer().getAbsolutePath() + d + currentWorld.getName() + "/"),file);

        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Done!"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(cmd.getName().equalsIgnoreCase("loadworld")){
            if(sender instanceof Player){
                Player p = (Player)sender;
                ChestUser u = ChestUser.getUser(p);

                if(u.hasPermission(Rank.ADMIN)){
                    if(args.length == 1){
                        if(StringUtils.isValidInteger(args[0])){
                            Map map = Map.getMap(Integer.parseInt(args[0]));

                            if(map != null){
                                if(!TeamServerPlugin.LOADED_MAPS.contains(map)){
                                    try {
                                        String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? null : "/";
                                        File worldDir = new File(Bukkit.getWorldContainer() + d + map.getOriginalWorldName() + "/");
                                        if(worldDir.exists() || worldDir.isDirectory()) FileUtils.forceDelete(worldDir);
                                        map.loadMapToServer(false,true);
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mvimport " + map.getOriginalWorldName() + " normal -t flat");
                                        TeamServerPlugin.LOADED_MAPS.add(map);

                                        new BukkitRunnable(){
                                            @Override
                                            public void run() {
                                                World world = Bukkit.getWorld(map.getOriginalWorldName());
                                                if(world != null){
                                                    p.teleport(world.getSpawnLocation());
                                                } else {
                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Unknown world."));
                                                }
                                            }
                                        }.runTaskLater(TeamServerPlugin.getInstance(),2*20);
                                    } catch(Exception e){
                                        e.printStackTrace();
                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                                    }
                                } else {
                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Map is already loaded."));

                                    World world = Bukkit.getWorld(map.getOriginalWorldName());
                                    if(world != null){
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"prepareworld " + world.getName());
                                        p.teleport(world.getSpawnLocation());
                                    }
                                }
                            } else {
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Invalid map ID."));
                            }
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Please enter a valid number."));
                        }
                    } else {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "/" + label + " <Map-ID>");
                    }
                } else {
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You aren't permitted to execute this command."));
                }
            }
        }

        if(cmd.getName().equalsIgnoreCase("saveworld")){
            if(sender instanceof Player){
                Player p = (Player)sender;
                ChestUser u = ChestUser.getUser(p);

                if(u.hasPermission(Rank.ADMIN)){
                    World currentWorld = p.getWorld();

                    if(args.length == 1){
                        if(TeamServerPlugin.MAY_SAVE_WORLD){
                            if(args[0].equalsIgnoreCase("lobby")){
                                if(currentWorld.getName().equalsIgnoreCase("ChestHubV3")){
                                    TeamServerPlugin.MAY_SAVE_WORLD = false;

                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Forcing world save.."));

                                    /*if(!currentWorld.isAutoSave()) currentWorld.setAutoSave(true);
                                    currentWorld.save();*/
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save");

                                    new BukkitRunnable(){
                                        @Override
                                        public void run() {
                                            ChestAPI.async(() -> {
                                                try {
                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Saving world to premium lobbies.."));
                                                    saveLobbyTo(p,currentWorld,"/home/cloud/CNS-1/database/templates/PremiumLobby/maps/ChestHubV3#50/");

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Saving world to normal lobbies.."));
                                                    saveLobbyTo(p,currentWorld,"/home/cloud/CNS-1/database/templates/Lobby/maps/ChestHubV3#50/");

                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                } catch(Exception e){
                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                                                    e.printStackTrace();
                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                }
                                            });
                                        }
                                    }.runTaskLater(TeamServerPlugin.getInstance(),2*20);
                                } else {
                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("This is not a valid lobby world."));
                                }
                            } else if(args[0].equalsIgnoreCase("wodlobby")){
                                if(currentWorld.getName().equalsIgnoreCase("wodlobby")){
                                    TeamServerPlugin.MAY_SAVE_WORLD = false;

                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Forcing world save.."));
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save");

                                    new BukkitRunnable(){
                                        @Override
                                        public void run() {
                                            ChestAPI.async(() -> {
                                                try {
                                                    File file = new File("/home/wod/wrapper/local/templates/Lobby/default/" + currentWorld.getName() + "/");
                                                    if(file.exists() || file.isDirectory()){
                                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Found old file - deleting.."));
                                                        FileUtils.deleteDirectory(file);
                                                    } else {
                                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Old file not found - skipping delete step."));
                                                    }

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Copying world to lobby directory.."));
                                                    String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? null : "/";
                                                    FileUtils.copyDirectory(new File(Bukkit.getWorldContainer().getAbsolutePath() + d + currentWorld.getName() + "/"),file);

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Done!"));

                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                } catch(Exception e){
                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                                                    e.printStackTrace();
                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                }
                                            });
                                        }
                                    }.runTaskLater(TeamServerPlugin.getInstance(),2*20);
                                } else {
                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("This is not a valid lobby world."));
                                }
                            } else if(args[0].equalsIgnoreCase("wodoverworld")){
                                if(currentWorld.getName().equalsIgnoreCase("wod")){
                                    TeamServerPlugin.MAY_SAVE_WORLD = false;

                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Forcing world save.."));
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save");

                                    new BukkitRunnable(){
                                        @Override
                                        public void run() {
                                            ChestAPI.async(() -> {
                                                try {
                                                    File file = new File("/home/wod/wrapper/local/templates/Test/default/" + currentWorld.getName() + "/");
                                                    if(file.exists() || file.isDirectory()){
                                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Found old file - deleting.."));
                                                        FileUtils.deleteDirectory(file);
                                                    } else {
                                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Old file not found - skipping delete step."));
                                                    }

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Copying world to server directory.."));
                                                    String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? null : "/";
                                                    FileUtils.copyDirectory(new File(Bukkit.getWorldContainer().getAbsolutePath() + d + currentWorld.getName() + "/"),file);

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Done!"));

                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                } catch(Exception e){
                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                                                    e.printStackTrace();
                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                }
                                            });
                                        }
                                    }.runTaskLater(TeamServerPlugin.getInstance(),4*20);
                                } else {
                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("This is not a valid lobby world."));
                                }
                            } else if(args[0].equalsIgnoreCase("woddungeons")){
                                if(currentWorld.getName().equalsIgnoreCase("Dungeons")){
                                    TeamServerPlugin.MAY_SAVE_WORLD = false;

                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Forcing world save.."));
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save");

                                    new BukkitRunnable(){
                                        @Override
                                        public void run() {
                                            ChestAPI.async(() -> {
                                                try {
                                                    File file = new File("/home/wod/wrapper/local/templates/Test/default/" + currentWorld.getName() + "/");
                                                    if(file.exists() || file.isDirectory()){
                                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Found old file - deleting.."));
                                                        FileUtils.deleteDirectory(file);
                                                    } else {
                                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Old file not found - skipping delete step."));
                                                    }

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Copying world to server directory.."));
                                                    String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? null : "/";
                                                    FileUtils.copyDirectory(new File(Bukkit.getWorldContainer().getAbsolutePath() + d + currentWorld.getName() + "/"),file);

                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Done!"));

                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                } catch(Exception e){
                                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                                                    e.printStackTrace();
                                                    TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                }
                                            });
                                        }
                                    }.runTaskLater(TeamServerPlugin.getInstance(),4*20);
                                } else {
                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("This is not a valid lobby world."));
                                }
                            } else {
                                if(StringUtils.isValidInteger(args[0])){
                                    int mapID = Integer.parseInt(args[0]);
                                    Map map = Map.getMap(mapID);

                                    if(map != null){
                                        if(map.getOriginalWorldName().equals(currentWorld.getName())){
                                            TeamServerPlugin.MAY_SAVE_WORLD = false;
                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Forcing world save.."));

                                            /*if(!currentWorld.isAutoSave()) currentWorld.setAutoSave(true);
                                            currentWorld.save();*/
                                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"save");

                                            new BukkitRunnable(){
                                                @Override
                                                public void run() {
                                                    ChestAPI.async(() -> {
                                                        try {
                                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Creating new zip file.."));
                                                            String d = Bukkit.getWorldContainer().getAbsolutePath().endsWith("/") ? null : "/";
                                                            ZipFile zipFile = new ZipFile(Bukkit.getWorldContainer().getAbsolutePath() + d + StringUtils.randomInteger(10,5000) + ".zip");
                                                            ZipParameters parameters = new ZipParameters();
                                                            parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
                                                            parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
                                                            zipFile.addFolder(Bukkit.getWorldContainer().getAbsolutePath() + d + currentWorld.getName() + "/",parameters);

                                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Updating database entry.."));
                                                            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("UPDATE `maps` SET `zipFile` = ? WHERE `id` = ?");
                                                            FileInputStream stream = new FileInputStream(zipFile.getFile());
                                                            ps.setBlob(1, stream);
                                                            ps.setInt(2,map.getID());
                                                            ps.executeUpdate();
                                                            ps.close();
                                                            stream.close();

                                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Deleting local zip file.."));
                                                            FileUtils.forceDelete(zipFile.getFile());

                                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Done!"));
                                                            TeamServerPlugin.MAY_SAVE_WORLD = true;

                                                            ChestAPI.sync(() -> {
                                                                for(Player a : currentWorld.getPlayers()){
                                                                    a.teleport(Bukkit.getServer().getWorlds().get(0).getSpawnLocation());
                                                                }

                                                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mvremove " + map.getOriginalWorldName());
                                                                map.unregister();
                                                            });
                                                        } catch(Exception e){
                                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occurred."));
                                                            e.printStackTrace();
                                                            TeamServerPlugin.MAY_SAVE_WORLD = true;
                                                        }
                                                    });
                                                }
                                            }.runTaskLater(TeamServerPlugin.getInstance(),2*20);
                                        } else {
                                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("This is not a valid world."));
                                        }
                                    } else {
                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Invalid map ID."));
                                    }
                                } else {
                                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Please enter a valid number."));
                                }
                            }
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You may only save 1 world at once."));
                        }
                    } else {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "/" + label + " <Map-ID|lobby|wodlobby|wodoverworld|woddungeons>");
                    }
                } else {
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You aren't permitted to execute this command."));
                }
            } else {
                sender.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "You must be a player to do this!");
            }
        }

        if(cmd.getName().equalsIgnoreCase("bindreward")){
            if(sender instanceof Player){
                Player p = (Player)sender;
                ChestUser u = ChestUser.getUser(p);

                if(u.hasPermission(Rank.ADMIN)){
                    if(args.length == 2 && StringUtils.isValidInteger(args[0]) && StringUtils.isValidInteger(args[1])){
                        int achievementID = Integer.parseInt(args[0]);
                        int coins = Integer.parseInt(args[1]);

                        Achievement a = null;
                        if(achievementID > 0) a = Achievement.getAchievement(achievementID);
                        if(coins < 0) coins = 0;

                        if(!TeamServerPlugin.getInstance().isBinding(p)){
                            TeamServerPlugin.getInstance().setBinding(p,a,coins);

                            p.sendMessage(" ");
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.DARK_RED + "You are now in binding mode!");
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.YELLOW + "Click a chest head to bind the following:");

                            if(a != null){
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + "Achievement: " + ChatColor.YELLOW + a.getTitle());
                            } else {
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + "Achievement: " + ChatColor.YELLOW + "NONE");
                            }

                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + "Coins: " + ChatColor.YELLOW + String.valueOf(coins));
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "Use " + ChatColor.YELLOW + " /" + label + " cancel " + ChatColor.RED + "to leave binding mode!");
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You are already binding a reward. Type /" + label + " to cancel."));
                        }
                    } else if(args.length == 1 && args[0].equalsIgnoreCase("cancel")){
                        if(TeamServerPlugin.getInstance().isBinding(p)) TeamServerPlugin.getInstance().cancelBinding(p);

                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("You are no longer in binding mode!"));
                    } else {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "/" + label + " <Achievement-ID> <Amount of Coins>");
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "Enter " + ChatColor.YELLOW + "0" + ChatColor.RED + " to skip a value!");
                    }
                } else {
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You aren't permitted to execute this command."));
                }
            } else {
                sender.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "You must be a player to do this!");
            }
        }

        if(cmd.getName().equalsIgnoreCase("prepareworld")){
            if(sender instanceof Player){
                Player p = (Player)sender;
                ChestUser u = ChestUser.getUser(p);

                if(u.hasPermission(Rank.ADMIN)){
                    if(args.length == 1){
                        World w = Bukkit.getWorld(args[0]);

                        if(w != null){
                            w.setDifficulty(Difficulty.PEACEFUL);
                            w.setStorm(false);
                            w.setThundering(false);
                            w.setGameRuleValue("doDaylightCycle","false");
                            w.setGameRuleValue("doEntityDrops","false");
                            w.setGameRuleValue("doFireTick","false");
                            w.setGameRuleValue("doMobLoot","false");
                            w.setGameRuleValue("doMobSpawning","false");
                            w.setGameRuleValue("doTileDrops","false");
                            w.setGameRuleValue("mobGriefing","false");
                            w.setGameRuleValue("randomTickSpeed","3");

                            EntityType[] allowed = new EntityType[]{EntityType.ARMOR_STAND,EntityType.PAINTING,EntityType.ITEM_FRAME,EntityType.PLAYER};

                            for(Entity entity : w.getEntities()){
                                boolean despawn = true;

                                for(EntityType type : allowed) if(entity.getType() == type) despawn = false;

                                if(despawn) entity.remove();
                            }

                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Success!"));
                        } else {
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("That world could not be found."));
                        }
                    } else {
                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "/" + label + " <" + u.getTranslatedMessage("World") + ">");
                    }
                } else {
                    p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You aren't permitted to execute this command."));
                }
            } else {
                if(args.length == 1) {
                    World w = Bukkit.getWorld(args[0]);

                    if (w != null) {
                        w.setDifficulty(Difficulty.PEACEFUL);
                        w.setStorm(false);
                        w.setThundering(false);
                        w.setGameRuleValue("doDaylightCycle", "false");
                        w.setGameRuleValue("doEntityDrops", "false");
                        w.setGameRuleValue("doFireTick", "false");
                        w.setGameRuleValue("doMobLoot", "false");
                        w.setGameRuleValue("doMobSpawning", "false");
                        w.setGameRuleValue("doTileDrops", "false");
                        w.setGameRuleValue("mobGriefing", "false");
                        w.setGameRuleValue("randomTickSpeed", "3");

                        EntityType[] allowed = new EntityType[]{EntityType.ARMOR_STAND, EntityType.PAINTING, EntityType.ITEM_FRAME, EntityType.PLAYER};

                        for (Entity entity : w.getEntities()) {
                            boolean despawn = true;

                            for (EntityType type : allowed) if (entity.getType() == type) despawn = false;

                            if (despawn) entity.remove();
                        }

                        sender.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + "Success!");
                    } else {
                        sender.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + "That world could not be found.");
                    }
                }
            }
        }

        return false;
    }
}
