package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.tracker.ProjectileOpening;
import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** The only place that looses arrows; every arrow opens an attempt in the attack tracker. */
public final class BowShooter {
  // The middle of the player's body: arrows aimed at the feet hit the ground first.
  private static final double BODY_CENTER_FRACTION = 0.5;

  private final AttackTracker tracker;
  private final ServerClock clock;
  private final ShotParts parts;

  public BowShooter(AttackTracker tracker, ServerClock clock, ShotParts parts) {
    this.tracker = Objects.requireNonNull(tracker, "BowShooter.tracker");
    this.clock = Objects.requireNonNull(clock, "BowShooter.clock");
    this.parts = Objects.requireNonNull(parts, "BowShooter.parts");
  }

  public void shoot(Mob shooter, Player target, Attack attack) {
    PlayerId targetId = new PlayerId(target.getUniqueId());
    Vec3 velocity =
        parts
            .aim()
            .velocity(
                new ShotRequest(
                    attack,
                    PoseReader.positionOf(shooter.getEyeLocation()),
                    centerOf(target),
                    parts.movement().movementPerTick(targetId)));
    ArrowRotation rotation = parts.aim().rotationOf(velocity);
    Arrow arrow =
        shooter.launchProjectile(
            Arrow.class,
            new Vector(velocity.x(), velocity.y(), velocity.z()),
            launched -> prepare(launched, rotation));
    tracker.openProjectile(
        new ProjectileOpening(
            arrow.getUniqueId(),
            new MobId(shooter.getUniqueId()),
            targetId,
            attack,
            clock.currentTick(),
            TargetChecks.isInvulnerable(target)));
  }

  // Runs before the arrow enters the world, so players never see it facing the wrong way.
  private void prepare(Arrow arrow, ArrowRotation rotation) {
    arrow.setRotation(rotation.yaw(), rotation.pitch());
    parts.translator().forbidPickup(arrow);
  }

  private static Vec3 centerOf(Player target) {
    Location feet = target.getLocation();
    return new Vec3(
        feet.getX(), feet.getY() + target.getHeight() * BODY_CENTER_FRACTION, feet.getZ());
  }
}
