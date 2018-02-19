package eu.thechest.teamserver;

import eu.thechest.chestapi.achievement.Achievement;
import org.bukkit.entity.Player;

/**
 * Created by zeryt on 17.04.2017.
 */
public class BindingPlayer {
    public Player p;
    public Achievement achievement;
    public int coins;

    public BindingPlayer(Player p, Achievement toBind, int coinsToBind){
        this.p = p;
        this.achievement = toBind;
        this.coins = coinsToBind;
    }
}
