package io.github.nicodoou.mobai.bootstrap;

import io.github.nicodoou.mobai.adapter.runtime.RandomGroupIdSource;
import io.github.nicodoou.mobai.adapter.runtime.ServerTickCounter;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.application.ClosePlan;
import io.github.nicodoou.mobai.application.DescribeGroup;
import io.github.nicodoou.mobai.application.DescribePlayerMemory;
import io.github.nicodoou.mobai.application.DisbandGroup;
import io.github.nicodoou.mobai.application.GroupEvents;
import io.github.nicodoou.mobai.application.LoadMemories;
import io.github.nicodoou.mobai.application.RecordDamageTaken;
import io.github.nicodoou.mobai.application.RecordOutcome;
import io.github.nicodoou.mobai.application.RecordPlayerDeath;
import io.github.nicodoou.mobai.application.RecordingRandomSource;
import io.github.nicodoou.mobai.application.RecruitMob;
import io.github.nicodoou.mobai.application.RemoveMember;
import io.github.nicodoou.mobai.application.ResetMemories;
import io.github.nicodoou.mobai.application.SaveMemories;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.application.TickGroups;
import io.github.nicodoou.mobai.application.TraitCaptureMapper;
import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.strategy.TraitLedger;

/** The domain and the use cases, assembled once; no Paper here, so it is tested in JUnit. */
public record CoreServices(
    SettingsHolder settings,
    ServerTickCounter clock,
    RegroupWindow regroupWindow,
    ActiveGroups activeGroups,
    DomainEventPublisher events,
    GroupEvents groupEvents,
    RecordingRandomSource randomDraws,
    TickGroups tickGroups,
    RecordOutcome recordOutcome,
    RecordDamageTaken recordDamageTaken,
    RecordPlayerDeath recordPlayerDeath,
    RemoveMember removeMember,
    RecruitMob recruitMob,
    ResetMemories resetMemories,
    DescribeGroup describeGroup,
    DescribePlayerMemory describePlayerMemory,
    SaveMemories saveMemories,
    LoadMemories loadMemories,
    TraitLedger traitLedger,
    RecipeBase recipeBase) {

  public static CoreServices create(
      MobAiSettings initialSettings, MemoryRepository repository, RandomSource random) {
    Foundation foundation = foundation(initialSettings);
    Messaging messaging = messaging(foundation, random);
    return new CoreServices(
        foundation,
        messaging,
        new UseCases(combat(foundation, messaging), tools(foundation, repository)));
  }

  private CoreServices(Foundation foundation, Messaging messaging, UseCases useCases) {
    this(
        foundation.settings(),
        foundation.clock(),
        foundation.regroupWindow(),
        foundation.activeGroups(),
        messaging.events(),
        messaging.groupEvents(),
        messaging.randomDraws(),
        useCases.combat().tickGroups(),
        useCases.combat().recordOutcome(),
        useCases.combat().recordDamageTaken(),
        useCases.combat().recordPlayerDeath(),
        useCases.combat().removeMember(),
        useCases.combat().recruitMob(),
        useCases.tools().resetMemories(),
        useCases.tools().describeGroup(),
        useCases.tools().describePlayerMemory(),
        useCases.tools().saveMemories(),
        useCases.tools().loadMemories(),
        messaging.traitLedger(),
        messaging.recipeBase());
  }

  public StoredState storedState() {
    return new StoredState(
        clock.currentTick(),
        regroupWindow.currentTicks(),
        new TraitCaptureMapper().toStored(traitLedger.capture()),
        recipeBase.model());
  }

  public void restore(StoredState state) {
    clock.restore(state.serverTick());
    regroupWindow.restore(state.regroupWindowTicks());
    traitLedger.restore(new TraitCaptureMapper().toSums(state.traits()));
    state.base().ifPresent(recipeBase::replace);
  }

  private static Foundation foundation(MobAiSettings initialSettings) {
    SettingsHolder settings = new SettingsHolder(initialSettings);
    return new Foundation(
        settings,
        new ServerTickCounter(),
        new RegroupWindow(settings.section(MobAiSettings::retreat)),
        new ActiveGroups());
  }

  private static Messaging messaging(Foundation foundation, RandomSource random) {
    DomainEventPublisher publisher = new DomainEventPublisher();
    RecordingRandomSource randomDraws = new RecordingRandomSource(random);
    SettingsHolder settings = foundation.settings();
    BrainParts parts =
        BrainParts.standard(settings::current, randomDraws, foundation.regroupWindow());
    ClosePlan closePlan = new ClosePlan(foundation.activeGroups(), parts.recipePlanner());
    publisher.subscribe(PlanClosed.class, closePlan::execute);
    Brain brain = new Brain(settings::current, parts);
    return new Messaging(
        publisher,
        new GroupEvents(publisher),
        randomDraws,
        brain,
        parts.traitLedger(),
        parts.recipePlanner().base());
  }

  private static CombatUseCases combat(Foundation foundation, Messaging messaging) {
    ActiveGroups activeGroups = foundation.activeGroups();
    SettingsHolder settings = foundation.settings();
    GroupEvents groupEvents = messaging.groupEvents();
    return new CombatUseCases(
        new TickGroups(activeGroups, messaging.brain(), groupEvents),
        new RecordOutcome(activeGroups, settings),
        new RecordDamageTaken(activeGroups),
        new RecordPlayerDeath(activeGroups, settings, groupEvents),
        removeMember(foundation, groupEvents),
        new RecruitMob(activeGroups, settings, new RandomGroupIdSource()));
  }

  private static RemoveMember removeMember(Foundation foundation, GroupEvents groupEvents) {
    DisbandGroup disbandGroup = new DisbandGroup(foundation.activeGroups(), groupEvents);
    return new RemoveMember(foundation.activeGroups(), disbandGroup, foundation.regroupWindow());
  }

  private static ToolUseCases tools(Foundation foundation, MemoryRepository repository) {
    ActiveGroups activeGroups = foundation.activeGroups();
    return new ToolUseCases(
        new ResetMemories(activeGroups),
        new DescribeGroup(activeGroups),
        new DescribePlayerMemory(
            activeGroups, foundation.settings().section(MobAiSettings::success)),
        new SaveMemories(activeGroups, repository),
        new LoadMemories(activeGroups, foundation.settings(), repository));
  }

  private record Foundation(
      SettingsHolder settings,
      ServerTickCounter clock,
      RegroupWindow regroupWindow,
      ActiveGroups activeGroups) {}

  private record Messaging(
      DomainEventPublisher events,
      GroupEvents groupEvents,
      RecordingRandomSource randomDraws,
      Brain brain,
      TraitLedger traitLedger,
      RecipeBase recipeBase) {}

  private record CombatUseCases(
      TickGroups tickGroups,
      RecordOutcome recordOutcome,
      RecordDamageTaken recordDamageTaken,
      RecordPlayerDeath recordPlayerDeath,
      RemoveMember removeMember,
      RecruitMob recruitMob) {}

  private record ToolUseCases(
      ResetMemories resetMemories,
      DescribeGroup describeGroup,
      DescribePlayerMemory describePlayerMemory,
      SaveMemories saveMemories,
      LoadMemories loadMemories) {}

  private record UseCases(CombatUseCases combat, ToolUseCases tools) {}
}
