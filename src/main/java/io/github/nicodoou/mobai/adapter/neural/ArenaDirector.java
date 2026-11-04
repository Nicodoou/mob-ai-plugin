package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.adapter.command.GroupSpawner;
import io.github.nicodoou.mobai.adapter.command.SpawnRing;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.application.RemovalCause;
import io.github.nicodoou.mobai.application.RemoveMember;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.group.NeuralAssignment;
import io.github.nicodoou.mobai.domain.neural.ArenaGrid;
import io.github.nicodoou.mobai.domain.neural.ArenaGrid.ArenaCenter;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.settings.ArenaSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Takes players to the arenas and starts the fights there. */
public final class ArenaDirector {
  private static final double FULL_TURN_RADIANS = 2 * Math.PI;
  private static final int BOTH_SIDES = 2;

  private final ArenaSites sites;
  private final GroupSpawner spawner;
  private final DirectorParts parts;

  public ArenaDirector(ArenaSites sites, GroupSpawner spawner, DirectorParts parts) {
    this.sites = Objects.requireNonNull(sites, "ArenaDirector.sites");
    this.spawner = Objects.requireNonNull(spawner, "ArenaDirector.spawner");
    this.parts = Objects.requireNonNull(parts, "ArenaDirector.parts");
  }

  public boolean exists(String name) {
    return centerOf(name).isPresent();
  }

  public List<String> names() {
    return arena().names();
  }

  public CompletableFuture<ArenaSite> prepare(String name) {
    ArenaCenter center = centerOf(name).orElseThrow();
    return sites.prepare(center).thenApply(surface -> new ArenaSite(name, center, surface));
  }

  public GroupId fight(Player player, ArenaSite site, List<MobKind> kinds) {
    discardGroupsIn(site);
    player.teleport(site.surface());
    GroupId groupId =
        spawner.spawnPlaced(
            site.surface().getWorld(),
            placements(site.surface(), kinds),
            parts.settings().current().selection().defaultPolicy());
    parts
        .activeGroups()
        .group(groupId)
        .orElseThrow()
        .assignNeural(NeuralAssignment.forNetwork(site.name()));
    return groupId;
  }

  /** An arena's fight does not outlive the server: nothing would hold its chunks afterwards. */
  public void discardAll() {
    for (Group group : parts.activeGroups().groups()) {
      if (group.neuralAssignment().isPresent()) {
        discard(group);
      }
    }
  }

  private List<GroupSpawner.Placement> placements(Location surface, List<MobKind> kinds) {
    double angle = ThreadLocalRandom.current().nextDouble(FULL_TURN_RADIANS);
    double distance = arena().spawnDistanceBlocks();
    Vec3 clump =
        new Vec3(
            surface.getX() + distance * Math.cos(angle),
            surface.getY(),
            surface.getZ() + distance * Math.sin(angle));
    List<Vec3> positions = SpawnRing.positions(clump, kinds.size());
    List<GroupSpawner.Placement> placements = new ArrayList<>();
    for (int index = 0; index < kinds.size(); index++) {
      placements.add(placedOnGround(surface.getWorld(), kinds.get(index), positions.get(index)));
    }
    return placements;
  }

  private static GroupSpawner.Placement placedOnGround(World world, MobKind kind, Vec3 position) {
    Location ground = ArenaSites.surfaceAt(world, position.x(), position.z());
    return new GroupSpawner.Placement(kind, new Vec3(ground.getX(), ground.getY(), ground.getZ()));
  }

  private void discardGroupsIn(ArenaSite site) {
    for (Group group : parts.activeGroups().groups()) {
      if (belongsTo(group, site)) {
        discard(group);
      }
    }
  }

  // Location also catches a group whose assignment was lost: one the server saved before a crash.
  private boolean belongsTo(Group group, ArenaSite site) {
    boolean isAssigned =
        group.neuralAssignment().filter(found -> found.network().equals(site.name())).isPresent();
    return isAssigned || group.roster().members().stream().anyMatch(member -> isIn(member, site));
  }

  private boolean isIn(Member member, ArenaSite site) {
    Entity entity = Bukkit.getEntity(member.id().value());
    if (entity == null || !entity.getWorld().equals(site.surface().getWorld())) {
      return false;
    }
    double reach = (double) arena().spacingBlocks() / BOTH_SIDES;
    return Math.abs(entity.getX() - site.center().x()) <= reach
        && Math.abs(entity.getZ() - site.center().z()) <= reach;
  }

  // remove() usually takes the member out through DeathListener; one that is not loaded needs this.
  private void discard(Group group) {
    long tick = parts.clock().currentTick();
    for (Member member : List.copyOf(group.roster().members())) {
      Optional.ofNullable(Bukkit.getEntity(member.id().value())).ifPresent(Entity::remove);
      parts.removeMember().execute(member.id(), RemovalCause.DESPAWNED, tick);
    }
  }

  private Optional<ArenaCenter> centerOf(String name) {
    return ArenaGrid.centerOf(arena(), name);
  }

  private ArenaSettings arena() {
    return parts.settings().current().neural().arena();
  }

  public record ArenaSite(String name, ArenaCenter center, Location surface) {
    public ArenaSite {
      Objects.requireNonNull(name, "ArenaSite.name");
      Objects.requireNonNull(center, "ArenaSite.center");
      Objects.requireNonNull(surface, "ArenaSite.surface");
    }
  }

  public record DirectorParts(
      ActiveGroups activeGroups,
      RemoveMember removeMember,
      ServerClock clock,
      SettingsHolder settings) {
    public DirectorParts {
      Objects.requireNonNull(activeGroups, "DirectorParts.activeGroups");
      Objects.requireNonNull(removeMember, "DirectorParts.removeMember");
      Objects.requireNonNull(clock, "DirectorParts.clock");
      Objects.requireNonNull(settings, "DirectorParts.settings");
    }
  }
}
