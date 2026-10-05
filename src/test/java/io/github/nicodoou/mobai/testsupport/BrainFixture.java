package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.brain.AttackSuggester;
import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.PlanEndDetector;
import io.github.nicodoou.mobai.domain.brain.RegroupRule;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.brain.RetreatRule;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyFactory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.strategy.StrategyCatalog;
import io.github.nicodoou.mobai.domain.target.KillTimeEstimator;
import io.github.nicodoou.mobai.domain.target.SpiderTargetRule;
import io.github.nicodoou.mobai.domain.target.TargetSelector;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds a brain, a group and its snapshots for the brain tests. */
public final class BrainFixture {
  public static final GroupId GROUP_ID = new GroupId(new UUID(0, 3));
  public static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  public static final PlayerId BOB = new PlayerId(new UUID(0, 11));
  public static final long START_TICK = 1000;
  public static final int CATALOG_ZOMBIES = 4;
  public static final int CATALOG_SKELETONS = 3;
  public static final int CATALOG_SPIDERS = 2;
  private static final int SPARE_INDEXES = 1000;

  private final MobAiSettings settings = TestSettings.defaults();
  private final RegroupWindow regroupWindow = new RegroupWindow(settings::retreat);
  private final Group group;
  private final Brain brain;

  private BrainFixture(SelectionPolicyType policy, RandomSource random) {
    this.group =
        new Group(
            GROUP_ID,
            policy,
            new GroupKnowledge(
                new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
    this.brain = new Brain(() -> settings, parts(random));
  }

  /** Random policy with every index fixed by hand. */
  public static BrainFixture scripted(ScriptedRandomSource random) {
    return new BrainFixture(SelectionPolicyType.RANDOM, random);
  }

  /** Random policy that picks the given strategy index and then the first attack every time. */
  public static BrainFixture choosingStrategy(int strategyIndex) {
    int[] indexes = new int[SPARE_INDEXES];
    indexes[0] = strategyIndex;
    return scripted(new ScriptedRandomSource().withIndexes(indexes));
  }

  public static BrainFixture seeded(long seed) {
    return new BrainFixture(SelectionPolicyType.THOMPSON_SAMPLING, new SeededRandomSource(seed));
  }

  public Group group() {
    return group;
  }

  public RegroupWindow regroupWindow() {
    return regroupWindow;
  }

  public Brain brain() {
    return brain;
  }

  public BrainResult decide(GroupSnapshot snapshot) {
    return brain.decide(group, snapshot);
  }

  public BrainResult decide(long tick, List<MobSnapshot> mobs, PlayerSnapshot... players) {
    return decide(snapshot(tick, mobs, players));
  }

  public void addMembers(List<MobSnapshot> mobs) {
    mobs.forEach(mob -> group.roster().addMember(mob.id(), mob.kind()));
  }

  /** The 4 zombies, 3 skeletons and 2 spiders of the catalog test group, already members. */
  public List<MobSnapshot> catalogGroup() {
    List<MobSnapshot> mobs = catalogMobs();
    addMembers(mobs);
    return mobs;
  }

  public static List<MobSnapshot> catalogMobs() {
    return new GroupSnapshotBuilder()
        .withZombies(CATALOG_ZOMBIES)
        .withSkeletons(CATALOG_SKELETONS)
        .withSpiders(CATALOG_SPIDERS)
        .build()
        .mobs();
  }

  public static GroupSnapshot snapshot(
      long tick, List<MobSnapshot> mobs, PlayerSnapshot... players) {
    return new GroupSnapshot(GROUP_ID, tick, mobs, List.of(players));
  }

  public static PlayerSnapshot alice() {
    return new PlayerSnapshotBuilder().withId(ALICE).build();
  }

  public static PlayerSnapshot bobAt(Vec3 position) {
    return new PlayerSnapshotBuilder().withId(BOB).withPosition(position).build();
  }

  public static List<MobSnapshot> withHealth(List<MobSnapshot> mobs, int index, double health) {
    MobSnapshot mob = mobs.get(index);
    return replaced(
        mobs,
        index,
        new MobSnapshot(mob.id(), mob.kind(), mob.position(), health, mob.maxHealth()));
  }

  public static List<MobSnapshot> withPosition(List<MobSnapshot> mobs, int index, Vec3 position) {
    MobSnapshot mob = mobs.get(index);
    return replaced(
        mobs,
        index,
        new MobSnapshot(mob.id(), mob.kind(), position, mob.health(), mob.maxHealth()));
  }

  private static List<MobSnapshot> replaced(List<MobSnapshot> mobs, int index, MobSnapshot mob) {
    List<MobSnapshot> copy = new ArrayList<>(mobs);
    copy.set(index, mob);
    return List.copyOf(copy);
  }

  private BrainParts parts(RandomSource random) {
    return new BrainParts(
        new TargetSelector(
            settings::target, new KillTimeEstimator(settings::target, settings::attack)),
        new SpiderTargetRule(settings::target),
        new StrategyCatalog(new CombatGeometry()),
        new SelectionPolicyFactory(settings::selection, random),
        new AttackSuggester(),
        new PlanEndDetector(settings::plan),
        new RetreatRule(settings::plan, settings::retreat),
        new RegroupRule(settings::retreat, regroupWindow),
        regroupWindow);
  }
}
