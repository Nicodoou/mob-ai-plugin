"""Trainer tests. No Minecraft: the plugin is simulated by PluginSim, which only sees policy.json."""

from __future__ import annotations

import csv
import json
import math
import os
import shutil
import tempfile
import threading
import time
import unittest
import uuid

import numpy as np

import train


def forward_like_plugin(policy: dict, obs: list[float]) -> np.ndarray:
    """Evaluates policy.json the way the published format describes, independent of train.Mlp."""
    x = np.asarray(obs, dtype=float)
    layers = policy["layers"]
    for index, layer in enumerate(layers):
        x = np.asarray(layer["w"]) @ x + np.asarray(layer["b"])
        if index < len(layers) - 1:
            x = np.tanh(x)
    return x


class PluginSim:
    """Writes experience exactly as the plugin would, sampling from the published policy.json."""

    def __init__(self, directory: str, rng: np.random.Generator, lag_versions: int = 0):
        self.directory = directory
        self.rng = rng
        self.lag_versions = lag_versions
        self.tick = 0
        self.fight_number = 0
        self.history: list[dict] = []
        self.policy: dict | None = None
        os.makedirs(directory, exist_ok=True)
        self.experience = open(os.path.join(directory, train.EXPERIENCE_FILE), "a", encoding="utf-8")

    def close(self) -> None:
        self.experience.close()

    def reload_policy(self) -> None:
        path = os.path.join(self.directory, train.POLICY_FILE)
        if not os.path.exists(path):
            return
        with open(path, encoding="utf-8") as source:
            published = json.load(source)
        if not self.history or self.history[-1]["version"] != published["version"]:
            self.history.append(published)
        self.policy = self.history[max(0, len(self.history) - 1 - self.lag_versions)]

    def choose(self, obs: list[float], mask: list[int]) -> tuple[int, float]:
        allowed = np.asarray(mask, dtype=bool)
        if self.policy is None:
            logits = np.zeros(len(mask))
        else:
            logits = forward_like_plugin(self.policy, obs)
        logits = np.where(allowed, logits, -np.inf)
        probabilities = np.exp(logits - logits.max())
        probabilities /= probabilities.sum()
        action = int(self.rng.choice(len(mask), p=probabilities))
        return action, float(math.log(probabilities[action]))

    def write(self, record: dict) -> None:
        self.experience.write(json.dumps(record) + "\n")

    def step(self, group: str, mob: str, obs: list[float], mask: list[int], action: int, logp: float) -> None:
        self.write({"t": "step", "tick": self.tick, "group": group, "mob": mob, "obs": obs, "mask": mask, "action": action, "logp": logp})

    def event(self, group: str, mob: str | None, kind: str, amount: float) -> None:
        self.write({"t": "event", "tick": self.tick, "group": group, "mob": mob, "kind": kind, "amount": amount})

    def end(self, group: str, winner: str) -> None:
        self.write({"t": "end", "tick": self.tick, "group": group, "winner": winner})
        self.experience.flush()

    def new_group(self) -> str:
        self.fight_number += 1
        return f"group-{self.fight_number}"


def quadrant_action(obs: list[float]) -> int:
    return (1 if obs[0] > 0 else 0) + (2 if obs[1] > 0 else 0)


def play_quadrant_fights(sim: PluginSim, fights: int, mobs: int = 4, steps: int = 20) -> tuple[float, float]:
    """The right action depends on an input. Returns the share of right actions and of fights won."""
    right = total = wins = 0
    for _ in range(fights):
        group = sim.new_group()
        ids = [str(uuid.UUID(int=int(sim.rng.integers(1 << 62)))) for _ in range(mobs)]
        fight_right = 0
        for _ in range(steps):
            for mob in ids:
                obs = [float(v) for v in sim.rng.uniform(-1.0, 1.0, 4)]
                correct = quadrant_action(obs)
                mask = [1, 1, 1, 1, 0, 0]
                wrong = (correct + 1 + int(sim.rng.integers(3))) % 4
                if sim.rng.random() < 0.3:
                    mask[wrong] = 0
                action, logp = sim.choose(obs, mask)
                sim.step(group, mob, obs, mask, action, logp)
                if action == correct:
                    fight_right += 1
                    sim.event(group, mob, "DAMAGE_DEALT", 1.0)
                else:
                    sim.event(group, mob, "DAMAGE_TAKEN", 1.0)
            sim.tick += train.STEP_TICKS
        right += fight_right
        total += mobs * steps
        won = fight_right > 0.6 * mobs * steps
        wins += won
        sim.end(group, "MOBS" if won else "PLAYER")
    return right / total, wins / fights


def play_corridor_fights(sim: PluginSim, fights: int, mobs: int = 3, length: int = 5, limit: int = 14) -> float:
    """Reaching the goal side takes many right moves and only the final result rewards it.

    The goal side is an input, the opposite wall kills, and the team wins with most mobs there.
    """
    wins = 0
    for _ in range(fights):
        group = sim.new_group()
        goal = 1 if sim.rng.random() < 0.5 else -1
        positions = {str(uuid.uuid4()): 0 for _ in range(mobs)}
        arrived: set[str] = set()
        dead: set[str] = set()
        for elapsed in range(limit):
            for mob, position in positions.items():
                if mob in arrived or mob in dead:
                    continue
                obs = [position / length, float(goal), elapsed / limit]
                action, logp = sim.choose(obs, [1, 1, 1, 0])
                sim.step(group, mob, obs, [1, 1, 1, 0], action, logp)
                positions[mob] = position + (action - 1)
                if positions[mob] == goal * length:
                    arrived.add(mob)
                elif positions[mob] == -goal * length:
                    dead.add(mob)
                    sim.event(group, mob, "MOB_DIED", 1.0)
            sim.tick += train.STEP_TICKS
            if len(arrived) + len(dead) == mobs:
                break
        won = len(arrived) * 2 > mobs
        wins += won
        sim.end(group, "MOBS" if won else "PLAYER")
    return wins / fights


class Workspace(unittest.TestCase):
    def setUp(self) -> None:
        self.root = tempfile.mkdtemp(prefix="trainer-test-")
        self.addCleanup(shutil.rmtree, self.root, True)
        self.metrics = train.MetricsLog(os.path.join(self.root, train.METRICS_FILE))

    def network(self, name: str, hp: train.Hyperparams | None = None) -> train.NetworkTrainer:
        os.makedirs(os.path.join(self.root, name), exist_ok=True)
        return train.NetworkTrainer(self.root, name, hp or train.Hyperparams(), self.metrics)

    def experience_path(self, name: str) -> str:
        return os.path.join(self.root, name, train.EXPERIENCE_FILE)

    def append(self, name: str, records: list[dict]) -> None:
        os.makedirs(os.path.join(self.root, name), exist_ok=True)
        with open(self.experience_path(name), "a", encoding="utf-8", newline="") as target:
            for record in records:
                target.write(json.dumps(record) + "\n")


def step_record(tick: int, group: str, mob: str, action: int = 0, obs: list[float] | None = None, mask: list[int] | None = None) -> dict:
    return {
        "t": "step",
        "tick": tick,
        "group": group,
        "mob": mob,
        "obs": obs if obs is not None else [0.5, -0.5],
        "mask": mask if mask is not None else [1, 1, 0],
        "action": action,
        "logp": math.log(0.5),
    }


def event_record(tick: int, group: str, mob: str | None, kind: str, amount: float = 1.0) -> dict:
    return {"t": "event", "tick": tick, "group": group, "mob": mob, "kind": kind, "amount": amount}


def end_record(tick: int, group: str, winner: str) -> dict:
    return {"t": "end", "tick": tick, "group": group, "winner": winner}


class GradientTest(unittest.TestCase):
    def finite_difference_check(self, net: train.Mlp, loss_and_grads) -> None:
        _, grads = loss_and_grads()
        epsilon = 1e-6
        rng = np.random.default_rng(5)
        checked = 0
        for parameter, grad in zip(net.params, grads):
            self.assertEqual(parameter.shape, grad.shape)
            flat_positions = rng.choice(parameter.size, size=min(12, parameter.size), replace=False)
            for position in flat_positions:
                index = np.unravel_index(position, parameter.shape)
                original = parameter[index]
                parameter[index] = original + epsilon
                above, _ = loss_and_grads()
                parameter[index] = original - epsilon
                below, _ = loss_and_grads()
                parameter[index] = original
                numeric = (above - below) / (2 * epsilon)
                self.assertAlmostEqual(numeric, grad[index], delta=1e-6 + 1e-4 * abs(numeric))
                checked += 1
        self.assertGreater(checked, 30)

    def test_policy_gradient_matches_finite_differences(self) -> None:
        rng = np.random.default_rng(1)
        policy = train.Mlp([5, 8, 8, 4], rng, 1.0)
        n = 40
        mask = rng.random((n, 4)) < 0.7
        mask[:, 0] = True
        action = np.array([rng.choice(np.flatnonzero(row)) for row in mask])
        batch = train.Batch(
            rng.normal(size=(n, 5)),
            mask,
            action,
            rng.normal(-1.2, 0.6, size=n),
            rng.normal(size=n),
            np.zeros(n),
        )

        def loss_and_grads():
            loss, grads, _ = train.policy_loss_and_grads(policy, batch, 0.2, 0.05)
            return loss, grads

        self.finite_difference_check(policy, loss_and_grads)

    def test_value_gradient_matches_finite_differences(self) -> None:
        rng = np.random.default_rng(2)
        value = train.Mlp([3, 6, 6, 1], rng, 1.0)
        obs = rng.normal(size=(30, 3))
        returns = rng.normal(size=30)
        self.finite_difference_check(value, lambda: train.value_loss_and_grads(value, obs, returns))


class MaskTest(unittest.TestCase):
    def test_forbidden_actions_get_no_probability_even_with_the_largest_logit(self) -> None:
        logits = np.array([[50.0, 0.0, -3.0, 900.0], [-800.0, 1.0, 2.0, 3.0]])
        mask = np.array([[False, True, True, False], [True, False, False, False]])
        p, log_p = train.masked_distribution(logits, mask)
        self.assertTrue(np.all(p[~mask] == 0.0))
        np.testing.assert_allclose(p.sum(axis=1), 1.0)
        np.testing.assert_allclose(p[1, 0], 1.0)
        self.assertTrue(np.all(np.isfinite(log_p)))

    def test_trained_published_policy_never_samples_a_forbidden_action(self) -> None:
        rng = np.random.default_rng(3)
        policy = train.Mlp([3, 64, 64, 5], rng, 1.0)
        published = {"layers": policy.layers_json()}
        for _ in range(2000):
            obs = rng.normal(size=3)
            mask = rng.random(5) < 0.5
            if not mask.any():
                mask[0] = True
            logits = forward_like_plugin(published, obs.tolist())[None, :]
            p, _ = train.masked_distribution(logits, mask[None, :])
            self.assertIn(int(rng.choice(5, p=p[0])), np.flatnonzero(mask))

    def test_step_with_a_forbidden_action_is_rejected(self) -> None:
        ledger = train.FightLedger(train.REWARD_PROFILES["3"], 0.99, 1000)
        ledger.feed_line(json.dumps(step_record(0, "g", "m", action=2)).encode())
        self.assertEqual(ledger.invalid_lines, 1)
        self.assertEqual(ledger.fights, {})


class IncrementalReadingTest(Workspace):
    def test_cut_line_waits_for_its_end(self) -> None:
        path = self.experience_path("net-1")
        os.makedirs(os.path.dirname(path))
        with open(path, "wb") as target:
            target.write(b'{"a":1}\n{"b":2}\n{"c":')
        lines = train.read_complete_lines(path, 0, 1024)
        self.assertEqual(lines, [b'{"a":1}', b'{"b":2}'])
        offset = sum(len(line) + 1 for line in lines)
        self.assertEqual(train.read_complete_lines(path, offset, 1024), [])
        with open(path, "ab") as target:
            target.write(b"3}\r\n")
        self.assertEqual(train.read_complete_lines(path, offset, 1024), [b'{"c":3}\r'])

    def test_network_keeps_its_offset_on_a_cut_step_and_finishes_it_later(self) -> None:
        self.append("net-1", [step_record(0, "g", "m")])
        complete = os.path.getsize(self.experience_path("net-1"))
        cut = json.dumps(step_record(10, "g", "m")).encode()
        with open(self.experience_path("net-1"), "ab") as target:
            target.write(cut[:25])
        network = self.network("net-1")
        network.poll()
        self.assertEqual(network.offset, complete)
        self.assertEqual(len(network.ledger.fights["g"].mobs["m"].action), 1)
        self.assertEqual(network.ledger.invalid_lines, 0)
        with open(self.experience_path("net-1"), "ab") as target:
            target.write(cut[25:] + b"\n")
        network.poll()
        self.assertEqual(len(network.ledger.fights["g"].mobs["m"].action), 2)
        self.assertEqual(network.ledger.invalid_lines, 0)

    def test_garbage_lines_are_skipped(self) -> None:
        with_garbage = self.experience_path("net-1")
        os.makedirs(os.path.dirname(with_garbage))
        with open(with_garbage, "wb") as target:
            target.write(b'not json\n[1,2]\n\xff\xfe\n{"t":"step"}\n')
        self.append("net-1", [step_record(0, "g", "m")])
        network = self.network("net-1")
        network.poll()
        self.assertEqual(network.ledger.invalid_lines, 4)
        self.assertEqual(len(network.ledger.fights["g"].mobs["m"].action), 1)

    def test_shrunken_file_is_read_from_the_start(self) -> None:
        self.append("net-1", [step_record(0, "g", "m"), step_record(10, "g", "m")])
        network = self.network("net-1")
        network.poll()
        os.remove(self.experience_path("net-1"))
        self.append("net-1", [step_record(0, "h", "n")])
        network.poll()
        self.assertEqual(network.offset, os.path.getsize(self.experience_path("net-1")))
        self.assertEqual(list(network.ledger.fights), ["h"])


class RewardTest(unittest.TestCase):
    PROFILE = train.RewardProfile("test", 1.0, -1.0, -10.0, -5.0, 100.0, -100.0, -50.0, 7.0, 0.5)

    def ledger(self, gamma: float = 1.0) -> train.FightLedger:
        return train.FightLedger(self.PROFILE, gamma, 1000)

    @staticmethod
    def feed(ledger: train.FightLedger, records: list[dict]) -> None:
        for record in records:
            ledger.feed_line(json.dumps(record).encode())

    def test_event_is_shared_among_living_mobs_plus_individual_part(self) -> None:
        ledger = self.ledger()
        self.feed(ledger, [step_record(0, "g", "a"), step_record(0, "g", "b"), event_record(1, "g", "a", "DAMAGE_DEALT", 4.0)])
        mobs = ledger.fights["g"].mobs
        self.assertAlmostEqual(mobs["a"].reward[0], 2.0 + 2.0)
        self.assertAlmostEqual(mobs["b"].reward[0], 2.0)

    def test_event_without_mob_has_only_the_team_part(self) -> None:
        ledger = self.ledger()
        self.feed(ledger, [step_record(0, "g", "a"), step_record(0, "g", "b"), event_record(1, "g", None, "DAMAGE_TAKEN", 2.0)])
        mobs = ledger.fights["g"].mobs
        self.assertAlmostEqual(mobs["a"].reward[0], -1.0)
        self.assertAlmostEqual(mobs["b"].reward[0], -1.0)

    def test_dead_mob_stops_sharing_and_outcome_reaches_it_discounted(self) -> None:
        ledger = self.ledger(gamma=0.5)
        self.feed(
            ledger,
            [
                step_record(0, "g", "a"),
                step_record(0, "g", "b"),
                event_record(1, "g", "a", "MOB_DIED"),
                step_record(10, "g", "b"),
                event_record(11, "g", None, "DAMAGE_DEALT", 3.0),
                step_record(20, "g", "b"),
                end_record(20, "g", "MOBS"),
            ],
        )
        tracks = {t.mob: t for t in ledger.ready}
        self.assertAlmostEqual(tracks["a"].reward[0], -5.0 - 5.0 + 100.0 * 0.5**2)
        self.assertAlmostEqual(tracks["b"].reward[0], -5.0)
        self.assertAlmostEqual(tracks["b"].reward[1], 3.0)
        self.assertAlmostEqual(tracks["b"].reward[2], 100.0 + 7.0)

    def test_suffix_chooses_the_profile(self) -> None:
        self.assertEqual(train.profile_for("arena-1").label, "aggressive")
        self.assertEqual(train.profile_for("arena-2").label, "survival")
        self.assertEqual(train.profile_for("arena-3").label, "balanced")
        self.assertEqual(train.profile_for("arena-4").label, "victory")

    def test_fight_without_end_is_discarded_after_the_timeout(self) -> None:
        ledger = self.ledger()
        self.feed(ledger, [step_record(0, "old", "a"), step_record(1500, "new", "b")])
        self.assertNotIn("old", ledger.fights)
        self.assertEqual(ledger.discarded_fights, 1)
        self.assertEqual(ledger.ready, [])

    def test_ticks_going_back_discard_open_fights(self) -> None:
        ledger = self.ledger()
        self.feed(ledger, [step_record(5000, "old", "a"), step_record(10, "new", "b")])
        self.assertEqual(list(ledger.fights), ["new"])
        self.assertEqual(ledger.discarded_fights, 1)


class GaeTest(unittest.TestCase):
    def test_two_trajectories_do_not_leak_into_each_other(self) -> None:
        rewards = np.array([0.0, 1.0, 10.0])
        values = np.zeros(3)
        advantages, returns = train.compute_gae(rewards, values, [2, 1], 0.5, 1.0)
        np.testing.assert_allclose(advantages, [0.5, 1.0, 10.0])
        np.testing.assert_allclose(returns, advantages)


class PublishingTest(Workspace):
    SMALL = train.Hyperparams(batch_steps=40, minibatch=16, epochs=2)

    def small_fight(self, name: str, group: str, start: int) -> None:
        records = []
        for t in range(11):
            tick = start + t * 10
            records.append(step_record(tick, group, "a", action=t % 2))
            records.append(step_record(tick, group, "b", action=(t + 1) % 2))
            records.append(event_record(tick, group, "a", "DAMAGE_DEALT", 1.0))
        records.append(end_record(start + 110, group, "MOBS"))
        self.append(name, records)

    def test_batch_far_from_the_current_policy_does_not_update_it(self) -> None:
        records = []
        for t in range(25):
            record = step_record(t * 10, "g", "a")
            record["logp"] = -30.0
            records.append(record)
            records.append(step_record(t * 10, "g", "b"))
            records[-1]["logp"] = -30.0
        records.append(end_record(250, "g", "MOBS"))
        self.append("net-1", records)
        network = self.network("net-1", self.SMALL)
        with self.assertLogs("trainer", level="WARNING"):
            network.poll()
        self.assertEqual(network.version, 1)
        self.assertEqual(network.ledger.ready_steps, 0)
        self.assertEqual(network.total_steps, 50)

    def test_stale_steps_are_dropped_and_fresh_ones_still_train(self) -> None:
        records = []
        for t in range(50):
            record = step_record(t * 10, "g", "a")
            if t % 2 == 0:
                record["logp"] = -30.0
            records.append(record)
        records.append(end_record(500, "g", "MOBS"))
        self.append("net-1", records)
        network = self.network("net-1", self.SMALL)
        network.poll()
        self.assertEqual(network.version, 2)
        with open(self.metrics.path, encoding="utf-8") as source:
            self.assertEqual(float(list(csv.DictReader(source))[-1]["stale_fraction"]), 0.5)

    def test_nothing_is_published_without_data(self) -> None:
        network = self.network("net-1", self.SMALL)
        self.assertFalse(network.poll())
        self.assertFalse(os.path.exists(os.path.join(network.directory, train.POLICY_FILE)))

    def test_policy_shape_comes_from_the_data_and_version_rises(self) -> None:
        self.small_fight("net-1", "g1", 0)
        self.small_fight("net-1", "g2", 200)
        network = self.network("net-1", self.SMALL)
        network.poll()
        with open(network.policy_path, encoding="utf-8") as source:
            policy = json.load(source)
        self.assertEqual(policy["version"], 2)
        self.assertEqual(np.shape(policy["layers"][0]["w"]), (64, 2))
        self.assertEqual(np.shape(policy["layers"][1]["w"]), (64, 64))
        self.assertEqual(np.shape(policy["layers"][2]["w"]), (3, 64))
        logits = forward_like_plugin(policy, [0.5, -0.5])
        mine, _ = network.policy.forward(np.array([[0.5, -0.5]]))
        np.testing.assert_allclose(logits, mine[0])
        self.assertTrue(os.path.exists(network.checkpoint_path))
        self.assertTrue(os.listdir(os.path.join(network.directory, train.SNAPSHOT_DIR)))
        self.assertFalse(os.path.exists(network.policy_path + ".tmp"))

    def test_version_keeps_rising_if_the_checkpoint_is_lost(self) -> None:
        self.small_fight("net-1", "g1", 0)
        self.small_fight("net-1", "g2", 200)
        first = self.network("net-1", self.SMALL)
        first.poll()
        os.remove(first.checkpoint_path)
        os.remove(first.experience_path)
        self.small_fight("net-1", "g3", 0)
        second = self.network("net-1", self.SMALL)
        second.poll()
        self.assertEqual(second.version, first.version + 1)

    def test_resuming_after_every_read_equals_never_stopping(self) -> None:
        other_root = tempfile.mkdtemp(prefix="trainer-test-")
        self.addCleanup(shutil.rmtree, other_root, True)
        for fight in range(8):
            self.small_fight("net-1", f"g{fight}", fight * 200)
        self.append("net-1", [step_record(1600, "open", "a")])
        with open(self.experience_path("net-1"), "ab") as target:
            target.write(json.dumps(step_record(1610, "open", "a")).encode()[:20])
        os.makedirs(os.path.join(other_root, "net-1"))
        shutil.copyfile(self.experience_path("net-1"), os.path.join(other_root, "net-1", train.EXPERIENCE_FILE))
        hp = train.Hyperparams(batch_steps=40, minibatch=16, epochs=2, chunk_bytes=2000)

        whole = train.NetworkTrainer(self.root, "net-1", hp, self.metrics)
        while whole.poll():
            pass

        reads = 0
        worked = True
        parts = train.NetworkTrainer(other_root, "net-1", hp, self.metrics)
        while worked:
            worked = parts.poll()
            parts.save_checkpoint()
            parts = train.NetworkTrainer(other_root, "net-1", hp, self.metrics)
            reads += 1
        parts.poll()
        self.assertGreater(reads, 3)

        self.assertGreater(whole.version, 2)
        self.assertEqual(whole.version, parts.version)
        self.assertEqual(whole.total_steps, parts.total_steps)
        self.assertEqual(whole.offset, parts.offset)
        self.assertEqual(whole.ledger.finished_fights, parts.ledger.finished_fights)
        self.assertEqual(whole.ledger.ready_steps, parts.ledger.ready_steps)
        for mine, theirs in zip(whole.policy.params + whole.value.params, parts.policy.params + parts.value.params):
            np.testing.assert_array_equal(mine, theirs)
        for mine, theirs in zip(whole.policy_optimizer.second, parts.policy_optimizer.second):
            np.testing.assert_array_equal(mine, theirs)
        self.assertEqual(whole.ledger.fights["open"].mobs["a"].obs, parts.ledger.fights["open"].mobs["a"].obs)

    def test_error_in_one_network_does_not_stop_the_others(self) -> None:
        for name in ("azbad-1", "good-1"):
            self.small_fight(name, "g1", 0)
            self.small_fight(name, "g2", 200)
        with open(os.path.join(self.root, "azbad-1", train.CHECKPOINT_FILE), "wb") as target:
            target.write(b"this is not a checkpoint")
        trainer = train.Trainer(self.root, self.SMALL, 16)
        trainer.scan()
        with self.assertLogs("trainer", level="ERROR"):
            trainer.run_pass(threading.Event())
        self.assertEqual(trainer.networks["good-1"].version, 2)
        self.assertEqual(trainer.networks["azbad-1"].failures, 1)
        self.assertGreater(trainer.networks["azbad-1"].retry_at, 0.0)

    def test_max_networks_is_respected(self) -> None:
        for index in range(4):
            self.small_fight(f"net{index}-1", "g", 0)
        trainer = train.Trainer(self.root, self.SMALL, 3)
        with self.assertLogs("trainer", level="WARNING"):
            trainer.scan()
        self.assertEqual(len(trainer.networks), 3)


class LearningTest(Workspace):
    """Real learning through files: the simulated plugin only ever sees what is published."""

    HP = train.Hyperparams(batch_steps=4000, minibatch=256, epochs=6)

    def train_quadrants(self, name: str, seed: int, updates: int, lag_versions: int = 0) -> list[tuple[float, float]]:
        sim = PluginSim(os.path.join(self.root, name), np.random.default_rng(seed), lag_versions)
        self.addCleanup(sim.close)
        network = train.NetworkTrainer(self.root, name, self.HP, self.metrics)
        curve = []
        for _ in range(updates):
            sim.reload_policy()
            before = network.version
            curve.append(play_quadrant_fights(sim, fights=int(self.HP.batch_steps / 80) + 1))
            while network.poll():
                pass
            self.assertGreater(network.version, before)
        sim.reload_policy()
        curve.append(play_quadrant_fights(sim, fights=50))
        return curve

    def test_policy_learns_which_action_each_input_needs(self) -> None:
        started = time.monotonic()
        curve = self.train_quadrants("quadrants-3", seed=11, updates=25)
        print(f"\nquadrants-3 right-action share: {[round(c[0], 3) for c in curve]} ({time.monotonic() - started:.1f} s)")
        print(f"quadrants-3 win share: {[round(c[1], 3) for c in curve]}")
        self.assertLess(curve[0][0], 0.40)
        self.assertGreater(curve[-1][0], 0.85)
        self.assertGreater(curve[-1][1], 0.95)

    def test_policy_still_learns_when_the_plugin_lags_two_versions_behind(self) -> None:
        started = time.monotonic()
        curve = self.train_quadrants("lagging-3", seed=12, updates=25, lag_versions=2)
        print(f"\nlagging-3 right-action share: {[round(c[0], 3) for c in curve]} ({time.monotonic() - started:.1f} s)")
        self.assertLess(curve[0][0], 0.40)
        self.assertGreater(curve[-1][0], 0.60)

    def test_outcome_only_reward_is_credited_back_through_time(self) -> None:
        started = time.monotonic()
        sim = PluginSim(os.path.join(self.root, "corridor-4"), np.random.default_rng(13))
        self.addCleanup(sim.close)
        network = train.NetworkTrainer(self.root, "corridor-4", self.HP, self.metrics)
        curve = []
        for _ in range(25):
            sim.reload_policy()
            curve.append(play_corridor_fights(sim, fights=120))
            while network.poll():
                pass
        sim.reload_policy()
        curve.append(play_corridor_fights(sim, fights=200))
        print(f"\ncorridor-4 win share: {[round(c, 3) for c in curve]} ({time.monotonic() - started:.1f} s)")
        self.assertLess(curve[0], 0.25)
        self.assertGreater(curve[-1], 0.80)


if __name__ == "__main__":
    unittest.main()
