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
import java.util.List;
import java.util.Objects;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * The only place that draws bows and looses arrows; every arrow opens an attempt in the attack
 * tracker.
 */
public final class BowShooter {
  private final AttackTracker tracker;
  private final ServerClock clock;
  private final ShotParts parts;

  public BowShooter(AttackTracker tracker, ServerClock clock, ShotParts parts) {
    this.tracker = Objects.requireNonNull(tracker, "BowShooter.tracker");
    this.clock = Objects.requireNonNull(clock, "BowShooter.clock");
    this.parts = Objects.requireNonNull(parts, "BowShooter.parts");
  }

  public ShotRequest requestFor(Mob shooter, Player target, Attack attack) {
    return new ShotRequest(
        attack,
        PoseReader.positionOf(shooter.getEyeLocation()),
        PoseReader.bodyCenterOf(target),
        parts.movement().movementPerTick(new PlayerId(target.getUniqueId())));
  }

  public boolean isLaneClear(ShotRequest request, List<MovingAlly> allies) {
    return parts.aim().isLaneClear(request, allies);
  }

  public void draw(Mob shooter) {
    parts.translator().drawBow(shooter);
  }

  public void lower(Mob shooter) {
    parts.translator().lowerBow(shooter);
  }

  public void shoot(Mob shooter, Player target, Attack attack) {
    PlayerId targetId = new PlayerId(target.getUniqueId());
    Vec3 velocity = parts.aim().velocity(requestFor(shooter, target, attack));
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
}
