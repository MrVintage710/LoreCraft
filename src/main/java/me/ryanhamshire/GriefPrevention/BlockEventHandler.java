/*
    GriefPrevention Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.GriefPrevention;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Dispenser;
import org.bukkit.metadata.MetadataValue;

//event handlers related to blocks
public class BlockEventHandler implements Listener 
{
	//convenience reference to singleton datastore
	private DataStore dataStore;
	
	private ArrayList<Material> trashBlocks;
	
	//constructor
	public BlockEventHandler(DataStore dataStore)
	{
		this.dataStore = dataStore;
		
		//create the list of blocks which will not trigger a warning when they're placed outside of land claims
		this.trashBlocks = new ArrayList<Material>();
		this.trashBlocks.add(Material.COBBLESTONE);
		this.trashBlocks.add(Material.TORCH);
		this.trashBlocks.add(Material.DIRT);
		this.trashBlocks.add(Material.OAK_SAPLING);
		this.trashBlocks.add(Material.SPRUCE_SAPLING);
		this.trashBlocks.add(Material.BIRCH_SAPLING);
		this.trashBlocks.add(Material.JUNGLE_SAPLING);
		this.trashBlocks.add(Material.ACACIA_SAPLING);
		this.trashBlocks.add(Material.DARK_OAK_SAPLING);
		this.trashBlocks.add(Material.GRAVEL);
		this.trashBlocks.add(Material.SAND);
		this.trashBlocks.add(Material.TNT);
		this.trashBlocks.add(Material.CRAFTING_TABLE);
	}
	
	//when a player breaks a block...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockBreak(BlockBreakEvent breakEvent)
	{
		Player player = breakEvent.getPlayer();
		Block block = breakEvent.getBlock();
		
		//make sure the player is allowed to break at the location
		String noBuildReason = GriefPrevention.instance.allowBreak(player, block, block.getLocation(), breakEvent);
		if(noBuildReason != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
			breakEvent.setCancelled(true);
			return;
		}
	}
	
	//when a player places multiple blocks...
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void onBlocksPlace(BlockMultiPlaceEvent placeEvent)
	{
	    Player player = placeEvent.getPlayer();
	    
	    //don't track in worlds where claims are not enabled
        if(!GriefPrevention.instance.claimsEnabledForWorld(placeEvent.getBlock().getWorld())) return;
        
        //make sure the player is allowed to build at the location
        for(BlockState block : placeEvent.getReplacedBlockStates())
        {
            String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation(), block.getType());
            if(noBuildReason != null)
            {
                GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
                placeEvent.setCancelled(true);
                return;
            }
        }
	}
	
	private boolean doesAllowFireProximityInWorld(World world) {
		if (GriefPrevention.instance.pvpRulesApply(world)) {
			return GriefPrevention.instance.config_pvp_allowFireNearPlayers;
		} else {
			return GriefPrevention.instance.config_pvp_allowFireNearPlayers_NonPvp;
		}
	}
	
	//when a player places a block...
	@SuppressWarnings("null")
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onBlockPlace(BlockPlaceEvent placeEvent)
	{
		Player player = placeEvent.getPlayer();
		Block block = placeEvent.getBlock();
				
		//FEATURE: limit fire placement, to prevent PvP-by-fire
		
		//if placed block is fire and pvp is off, apply rules for proximity to other players 
		if(block.getType() == Material.FIRE && !doesAllowFireProximityInWorld(block.getWorld()))
		{
			List<Player> players = block.getWorld().getPlayers();
			for(int i = 0; i < players.size(); i++)
			{
				Player otherPlayer = players.get(i);

				// Ignore players in creative or spectator mode to avoid users from checking if someone is spectating near them
				if(otherPlayer.getGameMode() == GameMode.CREATIVE || otherPlayer.getGameMode() == GameMode.SPECTATOR) {
					continue;
				}

				Location location = otherPlayer.getLocation();
				if(!otherPlayer.equals(player) && location.distanceSquared(block.getLocation()) < 9 && player.canSee(otherPlayer))
				{
					GriefPrevention.sendMessage(player, TextMode.Err, Messages.PlayerTooCloseForFire2);
					placeEvent.setCancelled(true);
					return;
				}					
			}
		}
		
		//don't track in worlds where claims are not enabled
        if(!GriefPrevention.instance.claimsEnabledForWorld(placeEvent.getBlock().getWorld())) return;
		
		//make sure the player is allowed to build at the location
		String noBuildReason = GriefPrevention.instance.allowBuild(player, block.getLocation(), block.getType());
		if(noBuildReason != null)
		{
			GriefPrevention.sendMessage(player, TextMode.Err, noBuildReason);
			placeEvent.setCancelled(true);
			return;
		}
		
		//if the block is being placed within or under an existing claim
		PlayerData playerData = this.dataStore.getPlayerData(player.getUniqueId());
		Claim claim = this.dataStore.getClaimAt(block.getLocation(), true, playerData.lastClaim);
		if(claim != null)
		{
		    playerData.lastClaim = claim;
		    
			//warn about TNT not destroying claimed blocks
            if(block.getType() == Material.TNT && !claim.areExplosivesAllowed && playerData.siegeData == null)
            {
                GriefPrevention.sendMessage(player, TextMode.Warn, Messages.NoTNTDamageClaims);
                GriefPrevention.sendMessage(player, TextMode.Instr, Messages.ClaimExplosivesAdvertisement);
            }
			
			//if the player has permission for the claim and he's placing UNDER the claim
			if(block.getY() <= claim.lesserBoundaryCorner.getBlockY() && claim.allowBuild(player, block.getType()) == null)
			{
				//extend the claim downward
				this.dataStore.extendClaim(claim, block.getY() - GriefPrevention.instance.config_claims_claimsExtendIntoGroundDistance);
			}
			
			//allow for a build warning in the future 
			playerData.warnedAboutBuildingOutsideClaims = false;
		}
	}
	
	static boolean isActiveBlock(Block block)
	{
	    return isActiveBlock(block.getType());
	}
	
	static boolean isActiveBlock(BlockState state)
	{
	    return isActiveBlock(state.getType());
	}
	
	static boolean isActiveBlock(Material type)
	{
	    if(type == Material.HOPPER || type == Material.BEACON || type == Material.SPAWNER) return true;
	    return false;
	}
}
