package eu.thechest.teamserver;

import eu.thechest.chestapi.ChestAPI;
import eu.thechest.chestapi.maps.Map;
import eu.thechest.chestapi.mysql.MySQLManager;
import eu.thechest.chestapi.server.ServerSettingsManager;
import eu.thechest.chestapi.user.ChestUser;
import eu.thechest.chestapi.user.Rank;
import eu.thechest.chestapi.util.PlayerUtilities;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scoreboard.Team;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Created by zeryt on 17.04.2017.
 */
public class MainListener implements Listener {
    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);

        if(TeamServerPlugin.getInstance().isBinding(p)) TeamServerPlugin.getInstance().cancelBinding(p);
    }

    @EventHandler
    public void onUnload(WorldUnloadEvent e){
        Map m = null;

        for(Map map : TeamServerPlugin.LOADED_MAPS){
            if(e.getWorld().getName().equalsIgnoreCase(map.getOriginalWorldName())) m = map;
        }

        if(m != null) TeamServerPlugin.LOADED_MAPS.remove(m);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);

        if(TeamServerPlugin.getInstance().isBinding(p)){
            e.setCancelled(true);
            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You cannot break blocks in binding mode!"));
        } else {
            if(e.getBlock() != null){
                if(e.getBlock().getType() == Material.SKULL){
                    ChestAPI.async(() -> {
                        Skull s = (Skull)e.getBlock().getState();
                        Location loc = e.getBlock().getLocation();

                        if(s.hasOwner() && s.getOwner() != null && s.getOwner().equalsIgnoreCase("Zealock")){
                            try {
                                PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `chestHeads` WHERE `world`=? AND `x`=? AND `y`=? AND `z`=?");
                                ps.setString(1,loc.getWorld().getName());
                                ps.setInt(2,loc.getBlockX());
                                ps.setInt(3,loc.getBlockY());
                                ps.setInt(4,loc.getBlockZ());

                                ResultSet rs = ps.executeQuery();
                                if(rs.first()){
                                    if(u.hasPermission(Rank.ADMIN)){
                                        PreparedStatement d = MySQLManager.getInstance().getConnection().prepareStatement("DELETE FROM `chestHeads` WHERE `world`=? AND `x`=? AND `y`=? AND `z`=?");
                                        d.setString(1,loc.getWorld().getName());
                                        d.setInt(2,loc.getBlockX());
                                        d.setInt(3,loc.getBlockY());
                                        d.setInt(4,loc.getBlockZ());
                                        d.executeUpdate();

                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Reward removed."));
                                    } else {
                                        e.setCancelled(true);
                                        p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("You can't break this block as there is a reward bound to it."));
                                    }
                                }

                                MySQLManager.getInstance().closeResources(rs,ps);
                            } catch(Exception e1){
                                e1.printStackTrace();
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occured."));
                            }
                        }
                    });
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e){
        Player p = e.getPlayer();
        ChestUser u = ChestUser.getUser(p);

        if(TeamServerPlugin.getInstance().isBinding(p) && e.getAction() == Action.RIGHT_CLICK_BLOCK){
            if(e.getClickedBlock().getType() == Material.SKULL){
                Skull s = (Skull)e.getClickedBlock().getState();
                Location loc = e.getClickedBlock().getLocation();
                BindingPlayer b = TeamServerPlugin.getInstance().toBindingPlayer(p);

                if(s.hasOwner() && s.getOwner() != null && s.getOwner().equalsIgnoreCase("Zealock")){
                    ChestAPI.async(() -> {
                        try {
                            PreparedStatement ps = MySQLManager.getInstance().getConnection().prepareStatement("SELECT * FROM `chestHeads` WHERE `world`=? AND `x`=? AND `y`=? AND `z`=?");
                            ps.setString(1,loc.getWorld().getName());
                            ps.setInt(2,loc.getBlockX());
                            ps.setInt(3,loc.getBlockY());
                            ps.setInt(4,loc.getBlockZ());

                            ResultSet rs = ps.executeQuery();
                            if(rs.first()){
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("There is already a reward bound to this block."));
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("Break it to remove the reward added by " + PlayerUtilities.getNameFromUUID(UUID.fromString(rs.getString("addedBy"))) + "."));
                                p.performCommand("bindreward cancel");
                            } else {
                                p.performCommand("bindreward cancel");
                                PreparedStatement insert = MySQLManager.getInstance().getConnection().prepareStatement("INSERT INTO `chestHeads` (`world`,`x`,`y`,`z`,`achievementToGive`,`coinsToGive`,`addedBy`) VALUES(?,?,?,?,?,?,?)");
                                insert.setString(1,loc.getWorld().getName());
                                insert.setInt(2,loc.getBlockX());
                                insert.setInt(3,loc.getBlockY());
                                insert.setInt(4,loc.getBlockZ());
                                if(b.achievement == null){
                                    insert.setInt(5,0);
                                } else {
                                    insert.setInt(5,b.achievement.getID());
                                }
                                insert.setInt(6,b.coins);
                                insert.setString(7,p.getUniqueId().toString());
                                insert.executeUpdate();
                                insert.close();
                                p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.GREEN + u.getTranslatedMessage("Success!"));
                            }

                            MySQLManager.getInstance().closeResources(rs,ps);
                        } catch(Exception e1){
                            e1.printStackTrace();
                            p.sendMessage(ServerSettingsManager.RUNNING_GAME.getPrefix() + ChatColor.RED + u.getTranslatedMessage("An error occured."));
                        }
                    });
                }
            }
        }
    }
}
