package io.github.nicodoou.mobai.adapter.snapshot;

import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Positions and the player's pose read from Paper, for snapshots and goals alike. */
public final class PoseReader {
  private PoseReader() {}

  public static Vec3 positionOf(Location location) {
    return new Vec3(location.getX(), location.getY(), location.getZ());
  }

  public static PlayerPose poseOf(Player player) {
    Location location = player.getLocation();
    return new PlayerPose(positionOf(location), EntityReadings.facingFromYaw(location.getYaw()));
  }
}
