package io.github.nicodoou.mobai.adapter.snapshot;

import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Positions and the player's pose read from Paper, for snapshots and goals alike. */
public final class PoseReader {
  // The middle of the body: what an arrow should hit, and what stands in its way.
  private static final double BODY_CENTER_FRACTION = 0.5;

  private PoseReader() {}

  public static Vec3 bodyCenterOf(LivingEntity entity) {
    Location feet = entity.getLocation();
    return new Vec3(
        feet.getX(), feet.getY() + entity.getHeight() * BODY_CENTER_FRACTION, feet.getZ());
  }

  public static Vec3 positionOf(Location location) {
    return new Vec3(location.getX(), location.getY(), location.getZ());
  }

  public static PlayerPose poseOf(Player player) {
    Location location = player.getLocation();
    return new PlayerPose(positionOf(location), EntityReadings.facingFromYaw(location.getYaw()));
  }
}
