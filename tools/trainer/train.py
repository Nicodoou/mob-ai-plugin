"""Overnight PPO trainer for MobAI networks, written with numpy only.

Reads experience appended by the plugin to <root>/<network>/experience.jsonl and
publishes <root>/<network>/policy.json. See README.md for how to run it.
"""

from __future__ import annotations

import argparse
import copy
import csv
import ctypes
import json
import logging
import logging.handlers
import math
import os
import shutil
import signal
import sys
import threading
import time
import zlib
from array import array
from collections import deque
from dataclasses import dataclass, field

# BLAS would otherwise spin one thread per core; the trainer must stay out of the servers' way.
for _thread_variable in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_thread_variable, "1")

import numpy as np  # noqa: E402


@dataclass(frozen=True)
class RewardProfile:
    label: str
    damage_dealt: float  # per point of damage
    damage_taken: float  # per point of damage
    mob_died: float  # per death
    ally_hit: float  # per point of damage, at least one point per hit
    win: float  # per mob, on winner == MOBS
    loss: float  # per mob, on winner == PLAYER
    draw: float  # per mob, on any other winner
    survivor: float  # per mob still alive at the end, unless the player won
    individual: float  # extra fraction of an event's value given to the mob named in it


REWARD_PROFILES = {
    "1": RewardProfile("aggressive", 0.10, -0.01, -0.10, -0.05, 1.0, -0.5, -0.5, 0.0, 0.5),
    "2": RewardProfile("survival", 0.02, -0.03, -2.00, -0.05, 1.0, -1.0, 0.0, 1.0, 0.5),
    "3": RewardProfile("balanced", 0.05, -0.03, -0.50, -0.05, 2.0, -2.0, -0.5, 0.25, 0.5),
    "4": RewardProfile("victory", 0.02, -0.005, 0.00, -0.01, 5.0, -5.0, -1.0, 0.0, 0.25),
}
DEFAULT_PROFILE_SUFFIX = "3"

HIDDEN_SIZES = (64, 64)
STEP_TICKS = 10
ALIVE_WINDOW_TICKS = 60
TICK_RESTART_SLACK = 200
SWEEP_INTERVAL_TICKS = 200
RECENT_FIGHTS = 50
ORTHOGONAL_HIDDEN_GAIN = math.sqrt(2.0)
POLICY_OUTPUT_GAIN = 0.01
VALUE_OUTPUT_GAIN = 1.0
LARGE_OBSERVATION = 10.0
REPLACE_ATTEMPTS = 10
REPLACE_RETRY_SECONDS = 0.2
MAX_BACKOFF_SECONDS = 300.0
BASE_BACKOFF_SECONDS = 5.0
MAX_LOG_RATIO = 20.0
LOG_BYTES = 5_000_000
LOG_BACKUPS = 2

EXPERIENCE_FILE = "experience.jsonl"
POLICY_FILE = "policy.json"
CHECKPOINT_FILE = "checkpoint.npz"
SNAPSHOT_DIR = "snapshots"
METRICS_FILE = "metrics.csv"
LOG_FILE = "trainer.log"

METRIC_COLUMNS = (
    "time",
    "network",
    "version",
    "total_steps",
    "fights",
    "mob_wins_last50",
    "mean_reward",
    "policy_loss",
    "value_loss",
    "entropy",
    "approx_kl",
    "clip_fraction",
    "stale_fraction",
    "explained_variance",
    "discarded_fights",
    "invalid_lines",
)

log = logging.getLogger("trainer")


@dataclass
class Hyperparams:
    batch_steps: int = 20_000
    minibatch: int = 512
    epochs: int = 6
    learning_rate: float = 3e-4
    value_learning_rate: float = 1e-3
    gamma: float = 0.995
    gae_lambda: float = 0.95
    clip: float = 0.2
    entropy: float = 0.01
    max_grad_norm: float = 0.5
    target_kl: float = 0.03
    max_logp_drift: float = math.log(4.0)
    fight_timeout_ticks: int = 18_000
    chunk_bytes: int = 4 * 1024 * 1024
    checkpoint_seconds: float = 300.0
    snapshot_seconds: float = 1800.0
    seed: int = 0


def orthogonal(rows: int, cols: int, gain: float, rng: np.random.Generator) -> np.ndarray:
    q, _ = np.linalg.qr(rng.standard_normal((max(rows, cols), min(rows, cols))))
    return gain * (q if rows >= cols else q.T)


class Mlp:
    """Fully connected network: w @ x + b per layer, tanh between layers, linear output."""

    def __init__(self, sizes: list[int], rng: np.random.Generator, output_gain: float):
        self.weights: list[np.ndarray] = []
        self.biases: list[np.ndarray] = []
        layer_count = len(sizes) - 1
        for index in range(layer_count):
            gain = output_gain if index == layer_count - 1 else ORTHOGONAL_HIDDEN_GAIN
            self.weights.append(orthogonal(sizes[index + 1], sizes[index], gain, rng))
            self.biases.append(np.zeros(sizes[index + 1]))

    @property
    def params(self) -> list[np.ndarray]:
        return [p for pair in zip(self.weights, self.biases) for p in pair]

    def forward(self, x: np.ndarray) -> tuple[np.ndarray, list[np.ndarray]]:
        inputs = []
        hidden = x
        last = len(self.weights) - 1
        for index, (w, b) in enumerate(zip(self.weights, self.biases)):
            inputs.append(hidden)
            output = hidden @ w.T + b
            hidden = np.tanh(output) if index < last else output
        return hidden, inputs

    def backward(self, inputs: list[np.ndarray], grad_output: np.ndarray) -> list[np.ndarray]:
        grads: list[np.ndarray] = [np.empty(0)] * (2 * len(self.weights))
        g = grad_output
        for index in reversed(range(len(self.weights))):
            grads[2 * index] = g.T @ inputs[index]
            grads[2 * index + 1] = g.sum(axis=0)
            if index > 0:
                g = (g @ self.weights[index]) * (1.0 - inputs[index] ** 2)
        return grads

    def layers_json(self) -> list[dict]:
        return [{"w": w.tolist(), "b": b.tolist()} for w, b in zip(self.weights, self.biases)]


class Adam:
    BETA1 = 0.9
    BETA2 = 0.999
    EPSILON = 1e-8

    def __init__(self, params: list[np.ndarray]):
        self.first = [np.zeros_like(p) for p in params]
        self.second = [np.zeros_like(p) for p in params]
        self.count = 0

    def step(self, params: list[np.ndarray], grads: list[np.ndarray], rate: float) -> None:
        self.count += 1
        first_scale = 1.0 - self.BETA1**self.count
        second_scale = 1.0 - self.BETA2**self.count
        for p, g, m, s in zip(params, grads, self.first, self.second):
            m += (1.0 - self.BETA1) * (g - m)
            s += (1.0 - self.BETA2) * (g * g - s)
            p -= rate * (m / first_scale) / (np.sqrt(s / second_scale) + self.EPSILON)


def clip_gradients(grads: list[np.ndarray], max_norm: float) -> None:
    norm = math.sqrt(sum(float(np.sum(g * g)) for g in grads))
    if norm > max_norm:
        scale = max_norm / (norm + 1e-12)
        for g in grads:
            g *= scale


def masked_distribution(logits: np.ndarray, mask: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Returns probabilities and log-probabilities; forbidden actions get p = 0 and log p = 0."""
    shifted = logits - np.max(np.where(mask, logits, -np.inf), axis=1, keepdims=True)
    exponentials = np.where(mask, np.exp(np.where(mask, shifted, 0.0)), 0.0)
    total = exponentials.sum(axis=1, keepdims=True)
    probabilities = exponentials / total
    log_probabilities = np.where(mask, shifted - np.log(total), 0.0)
    return probabilities, log_probabilities


@dataclass
class Batch:
    obs: np.ndarray
    mask: np.ndarray
    action: np.ndarray
    logp: np.ndarray
    advantage: np.ndarray
    returns: np.ndarray

    def __len__(self) -> int:
        return len(self.action)

    def select(self, indices: np.ndarray) -> "Batch":
        return Batch(
            self.obs[indices],
            self.mask[indices],
            self.action[indices],
            self.logp[indices],
            self.advantage[indices],
            self.returns[indices],
        )


def action_log_probability(policy: Mlp, batch: Batch) -> np.ndarray:
    logits, _ = policy.forward(batch.obs)
    _, log_probabilities = masked_distribution(logits, batch.mask)
    return log_probabilities[np.arange(len(batch)), batch.action]


def policy_loss_and_grads(
    policy: Mlp, batch: Batch, clip: float, entropy_coefficient: float
) -> tuple[float, list[np.ndarray], dict[str, float]]:
    n = len(batch)
    rows = np.arange(n)
    logits, inputs = policy.forward(batch.obs)
    p, log_p = masked_distribution(logits, batch.mask)
    log_ratio = np.clip(log_p[rows, batch.action] - batch.logp, -MAX_LOG_RATIO, MAX_LOG_RATIO)
    ratio = np.exp(log_ratio)
    unclipped = ratio * batch.advantage
    clipped = np.clip(ratio, 1.0 - clip, 1.0 + clip) * batch.advantage
    unclipped_is_active = unclipped <= clipped
    entropy_per_sample = -np.sum(p * log_p, axis=1)
    surrogate = -float(np.mean(np.minimum(unclipped, clipped)))
    mean_entropy = float(np.mean(entropy_per_sample))
    loss = surrogate - entropy_coefficient * mean_entropy

    d_loss_d_logp = -np.where(unclipped_is_active, unclipped, 0.0)
    grad_logits = -p * d_loss_d_logp[:, None]
    grad_logits[rows, batch.action] += d_loss_d_logp
    grad_logits += entropy_coefficient * p * (log_p + entropy_per_sample[:, None])
    grads = policy.backward(inputs, grad_logits / n)
    stats = {
        "policy_loss": surrogate,
        "entropy": mean_entropy,
        "clip_fraction": float(np.mean(np.abs(ratio - 1.0) > clip)),
    }
    return loss, grads, stats


def value_loss_and_grads(
    value: Mlp, obs: np.ndarray, returns: np.ndarray
) -> tuple[float, list[np.ndarray]]:
    output, inputs = value.forward(obs)
    error = output[:, 0] - returns
    grads = value.backward(inputs, error[:, None] / len(returns))
    return 0.5 * float(np.mean(error * error)), grads


def compute_gae(
    rewards: np.ndarray, values: np.ndarray, lengths: list[int], gamma: float, gae_lambda: float
) -> tuple[np.ndarray, np.ndarray]:
    """All trajectories end in a real terminal state, so nothing is bootstrapped past their end."""
    advantages = np.zeros_like(rewards)
    stop = len(rewards)
    for length in reversed(lengths):
        start = stop - length
        running = 0.0
        next_value = 0.0
        for t in range(stop - 1, start - 1, -1):
            delta = rewards[t] + gamma * next_value - values[t]
            running = delta + gamma * gae_lambda * running
            advantages[t] = running
            next_value = values[t]
        stop = start
    return advantages, advantages + values


def ppo_update(
    policy: Mlp,
    value: Mlp,
    policy_optimizer: Adam,
    value_optimizer: Adam,
    batch: Batch,
    hp: Hyperparams,
    rng: np.random.Generator,
) -> dict[str, float]:
    start_logp = action_log_probability(policy, batch)
    policy_active = True
    totals = {"policy_loss": 0.0, "entropy": 0.0, "clip_fraction": 0.0, "value_loss": 0.0}
    policy_steps = value_steps = 0
    approx_kl = 0.0
    for _ in range(hp.epochs):
        order = rng.permutation(len(batch))
        for start in range(0, len(batch), hp.minibatch):
            minibatch = batch.select(order[start : start + hp.minibatch])
            if policy_active:
                advantage = minibatch.advantage
                minibatch.advantage = (advantage - advantage.mean()) / (advantage.std() + 1e-8)
                _, grads, stats = policy_loss_and_grads(policy, minibatch, hp.clip, hp.entropy)
                clip_gradients(grads, hp.max_grad_norm)
                policy_optimizer.step(policy.params, grads, hp.learning_rate)
                for key, amount in stats.items():
                    totals[key] += amount
                policy_steps += 1
            loss, grads = value_loss_and_grads(value, minibatch.obs, minibatch.returns)
            clip_gradients(grads, hp.max_grad_norm)
            value_optimizer.step(value.params, grads, hp.value_learning_rate)
            totals["value_loss"] += loss
            value_steps += 1
        if policy_active:
            approx_kl = float(np.mean(start_logp - action_log_probability(policy, batch)))
            policy_active = approx_kl <= hp.target_kl
    result = {key: amount / max(policy_steps, 1) for key, amount in totals.items()}
    result["value_loss"] = totals["value_loss"] / max(value_steps, 1)
    result["approx_kl"] = approx_kl
    return result


class Track:
    """One mob's steps in one fight. Reward at index t is what happened after the step at t."""

    __slots__ = ("mob", "obs", "mask", "action", "logp", "reward", "alive", "last_tick")

    def __init__(self, mob: str, last_tick: int = 0, alive: bool = True):
        self.mob = mob
        self.obs = array("f")
        self.mask = array("B")
        self.action = array("H")
        self.logp = array("d")
        self.reward = array("d")
        self.alive = alive
        self.last_tick = last_tick


class Fight:
    __slots__ = ("group", "first_tick", "last_tick", "mobs")

    def __init__(self, group: str, first_tick: int, last_tick: int):
        self.group = group
        self.first_tick = first_tick
        self.last_tick = last_tick
        self.mobs: dict[str, Track] = {}


class FightLedger:
    """Turns raw JSON lines into finished per-mob trajectories, assigning rewards on the way."""

    TRACK_COLUMNS = (("obs", np.float32), ("mask", np.uint8), ("action", np.uint16), ("logp", np.float64), ("reward", np.float64))

    def __init__(self, profile: RewardProfile, gamma: float, fight_timeout_ticks: int):
        self.profile = profile
        self.gamma = gamma
        self.fight_timeout_ticks = fight_timeout_ticks
        self.fights: dict[str, Fight] = {}
        self.ready: list[Track] = []
        self.ready_steps = 0
        self.obs_dim: int | None = None
        self.action_dim: int | None = None
        self.net_tick: int | None = None
        self.next_sweep_tick = 0
        self.finished_fights = 0
        self.discarded_fights = 0
        self.invalid_lines = 0
        self.recent_mob_wins: deque[int] = deque(maxlen=RECENT_FIGHTS)

    def feed_line(self, line: bytes) -> None:
        line = line.strip()
        if not line:
            return
        try:
            record = json.loads(line)
            kind = record["t"]
            tick = int(record["tick"])
            group = str(record["group"])
            self._observe_tick(tick)
            if kind == "step":
                valid = self._on_step(record, tick, group)
            elif kind == "event":
                valid = self._on_event(record, tick, group)
            elif kind == "end":
                self._on_end(tick, group, record["winner"])
                valid = True
            else:
                valid = False
        except (ValueError, KeyError, TypeError, OverflowError):
            valid = False
        if not valid:
            self.invalid_lines += 1

    def discard_open_fights(self) -> None:
        self.discarded_fights += len(self.fights)
        self.fights.clear()

    def forget_ticks(self) -> None:
        self.net_tick = None
        self.next_sweep_tick = 0

    def take_ready(self) -> list[Track]:
        tracks = self.ready
        self.ready = []
        self.ready_steps = 0
        return tracks

    def _observe_tick(self, tick: int) -> None:
        if self.net_tick is not None and tick < self.net_tick - TICK_RESTART_SLACK:
            log.info("tick went back from %d to %d: server restart assumed", self.net_tick, tick)
            self.discard_open_fights()
            self.net_tick = tick
            self.next_sweep_tick = tick + SWEEP_INTERVAL_TICKS
            return
        self.net_tick = tick if self.net_tick is None else max(self.net_tick, tick)
        if tick >= self.next_sweep_tick:
            self.next_sweep_tick = tick + SWEEP_INTERVAL_TICKS
            expired = [g for g, f in self.fights.items() if tick - f.first_tick > self.fight_timeout_ticks]
            for group in expired:
                del self.fights[group]
            self.discarded_fights += len(expired)

    def _on_step(self, record: dict, tick: int, group: str) -> bool:
        mob = record["mob"]
        obs = record["obs"]
        mask = record["mask"]
        action = record["action"]
        logp = float(record["logp"])
        if mob is None or isinstance(action, bool) or not isinstance(action, int):
            return False
        if self.obs_dim is not None and (len(obs) != self.obs_dim or len(mask) != self.action_dim):
            return False
        if not 0 <= action < len(mask) or not mask[action]:
            return False
        if not math.isfinite(logp) or not math.isfinite(math.fsum(obs)):
            return False
        obs_values = array("f", obs)
        if self.obs_dim is None:
            self.obs_dim = len(obs)
            self.action_dim = len(mask)
        fight = self.fights.get(group)
        if fight is None:
            fight = self.fights[group] = Fight(group, tick, tick)
        fight.last_tick = tick
        track = fight.mobs.get(mob)
        if track is None:
            track = fight.mobs[mob] = Track(mob, tick)
        elif not track.alive:
            return False
        track.last_tick = tick
        track.obs.extend(obs_values)
        track.mask.extend(1 if allowed else 0 for allowed in mask)
        track.action.append(action)
        track.logp.append(logp)
        track.reward.append(0.0)
        return True

    def _event_value(self, kind: str, amount: float) -> float | None:
        profile = self.profile
        if kind == "DAMAGE_DEALT":
            return profile.damage_dealt * amount
        if kind == "DAMAGE_TAKEN":
            return profile.damage_taken * amount
        if kind == "MOB_DIED":
            return profile.mob_died
        if kind == "BOT_HIT_BY_ALLY":
            return profile.ally_hit * max(amount, 1.0)
        return None

    def _on_event(self, record: dict, tick: int, group: str) -> bool:
        kind = record["kind"]
        value = self._event_value(kind, float(record.get("amount") or 0.0))
        if value is None or not math.isfinite(value):
            return False
        fight = self.fights.get(group)
        if fight is None:
            return True
        fight.last_tick = tick
        named = fight.mobs.get(record.get("mob"))
        alive = [t for t in fight.mobs.values() if t.alive and tick - t.last_tick <= ALIVE_WINDOW_TICKS]
        if alive:
            share = value / len(alive)
            for track in alive:
                track.reward[-1] += share
        if named is not None:
            named.reward[-1] += value * self.profile.individual
            if kind == "MOB_DIED":
                named.alive = False
        return True

    def _on_end(self, tick: int, group: str, winner: str) -> None:
        fight = self.fights.pop(group, None)
        if fight is None:
            return
        profile = self.profile
        outcome = {"MOBS": profile.win, "PLAYER": profile.loss}.get(winner, profile.draw)
        for track in fight.mobs.values():
            # A mob that fell earlier still shares the result, discounted by how long ago it fell.
            elapsed_steps = max(0, tick - track.last_tick) / STEP_TICKS
            track.reward[-1] += outcome * self.gamma**elapsed_steps
            if track.alive and winner != "PLAYER":
                track.reward[-1] += profile.survivor
            self.ready.append(track)
            self.ready_steps += len(track.action)
        self.finished_fights += 1
        self.recent_mob_wins.append(1 if winner == "MOBS" else 0)

    def state(self) -> tuple[dict, dict[str, np.ndarray]]:
        tracks = list(self.ready)
        fights_meta = []
        for fight in self.fights.values():
            fights_meta.append(
                {
                    "group": fight.group,
                    "first_tick": fight.first_tick,
                    "last_tick": fight.last_tick,
                    "mobs": len(fight.mobs),
                }
            )
            tracks.extend(fight.mobs.values())
        arrays = {
            f"track_{name}": np.frombuffer(b"".join(getattr(t, name).tobytes() for t in tracks), dtype=dtype)
            for name, dtype in self.TRACK_COLUMNS
        }
        meta = {
            "ready": len(self.ready),
            "fights": fights_meta,
            "tracks": [
                {"mob": t.mob, "steps": len(t.action), "alive": t.alive, "last_tick": t.last_tick}
                for t in tracks
            ],
            "obs_dim": self.obs_dim,
            "action_dim": self.action_dim,
            "net_tick": self.net_tick,
            "next_sweep_tick": self.next_sweep_tick,
            "finished_fights": self.finished_fights,
            "discarded_fights": self.discarded_fights,
            "invalid_lines": self.invalid_lines,
            "recent_mob_wins": list(self.recent_mob_wins),
        }
        return meta, arrays

    def restore(self, meta: dict, arrays: dict[str, np.ndarray]) -> None:
        self.obs_dim = meta["obs_dim"]
        self.action_dim = meta["action_dim"]
        self.net_tick = meta["net_tick"]
        self.next_sweep_tick = meta["next_sweep_tick"]
        self.finished_fights = meta["finished_fights"]
        self.discarded_fights = meta["discarded_fights"]
        self.invalid_lines = meta["invalid_lines"]
        self.recent_mob_wins = deque(meta["recent_mob_wins"], maxlen=RECENT_FIGHTS)
        tracks = []
        position = 0
        for info in meta["tracks"]:
            track = Track(info["mob"], info["last_tick"], info["alive"])
            steps = info["steps"]
            stop = position + steps
            track.obs.frombytes(arrays["track_obs"][position * (self.obs_dim or 0) : stop * (self.obs_dim or 0)].tobytes())
            track.mask.frombytes(arrays["track_mask"][position * (self.action_dim or 0) : stop * (self.action_dim or 0)].tobytes())
            track.action.frombytes(arrays["track_action"][position:stop].tobytes())
            track.logp.frombytes(arrays["track_logp"][position:stop].tobytes())
            track.reward.frombytes(arrays["track_reward"][position:stop].tobytes())
            tracks.append(track)
            position = stop
        self.ready = tracks[: meta["ready"]]
        self.ready_steps = sum(len(t.action) for t in self.ready)
        self.fights = {}
        cursor = meta["ready"]
        for info in meta["fights"]:
            fight = Fight(info["group"], info["first_tick"], info["last_tick"])
            for track in tracks[cursor : cursor + info["mobs"]]:
                fight.mobs[track.mob] = track
            cursor += info["mobs"]
            self.fights[fight.group] = fight


def read_complete_lines(path: str, offset: int, max_bytes: int) -> list[bytes]:
    """Lines after `offset`, without terminators. A trailing unterminated line is left for later."""
    with open(path, "rb") as source:
        source.seek(offset)
        data = source.read(max_bytes)
    end = data.rfind(b"\n")
    if end < 0:
        if len(data) == max_bytes:
            raise ValueError(f"line longer than {max_bytes} bytes at offset {offset} in {path}")
        return []
    return data[:end].split(b"\n")


def replace_with_retry(source: str, target: str) -> None:
    """Windows refuses the rename while a reader holds the target open, so give it a moment."""
    for attempt in range(REPLACE_ATTEMPTS):
        try:
            os.replace(source, target)
            return
        except PermissionError:
            if attempt == REPLACE_ATTEMPTS - 1:
                raise
            time.sleep(REPLACE_RETRY_SECONDS)


def profile_for(network: str) -> RewardProfile:
    suffix = network.rsplit("-", 1)[-1]
    profile = REWARD_PROFILES.get(suffix)
    if profile is None:
        log.warning("%s: no known reward suffix, using %s", network, REWARD_PROFILES[DEFAULT_PROFILE_SUFFIX].label)
        return REWARD_PROFILES[DEFAULT_PROFILE_SUFFIX]
    return profile


class MetricsLog:
    def __init__(self, path: str):
        self.path = path

    def write(self, row: dict) -> None:
        try:
            is_new = not os.path.exists(self.path) or os.path.getsize(self.path) == 0
            with open(self.path, "a", newline="", encoding="utf-8") as target:
                writer = csv.DictWriter(target, fieldnames=METRIC_COLUMNS)
                if is_new:
                    writer.writeheader()
                writer.writerow(row)
        except OSError as error:
            log.warning("could not write metrics: %s", error)


class NetworkTrainer:
    def __init__(self, root: str, name: str, hp: Hyperparams, metrics: MetricsLog):
        self.name = name
        self.hp = hp
        self.metrics = metrics
        self.directory = os.path.join(root, name)
        self.experience_path = os.path.join(self.directory, EXPERIENCE_FILE)
        self.policy_path = os.path.join(self.directory, POLICY_FILE)
        self.checkpoint_path = os.path.join(self.directory, CHECKPOINT_FILE)
        self.profile = profile_for(name)
        self.ledger = FightLedger(self.profile, hp.gamma, hp.fight_timeout_ticks)
        self.rng = np.random.default_rng([hp.seed, zlib.crc32(name.encode("utf-8"))])
        self.policy: Mlp | None = None
        self.value: Mlp | None = None
        self.policy_optimizer: Adam | None = None
        self.value_optimizer: Adam | None = None
        self.offset = 0
        self.version = 0
        self.total_steps = 0
        self.started = False
        self.dirty = False
        self.publish_pending = False
        self.last_checkpoint_time = time.monotonic()
        self.last_snapshot_time: float | None = None
        self.failures = 0
        self.retry_at = 0.0
        self.warned_large_obs = False

    def poll(self) -> bool:
        """Reads at most one chunk and trains at most once. Returns whether anything was done."""
        if not self.started:
            self._start()
        if self.publish_pending:
            self._publish()
        consumed = self._consume()
        if self.policy is None and self.ledger.obs_dim is not None:
            self._initialise_networks()
            self._publish()
        updated = False
        if self.ledger.ready_steps >= self.hp.batch_steps:
            self._update()
            updated = True
        if self.dirty and time.monotonic() - self.last_checkpoint_time >= self.hp.checkpoint_seconds:
            self.save_checkpoint()
        return consumed or updated

    def _start(self) -> None:
        if os.path.exists(self.checkpoint_path):
            self._load_checkpoint()
            if self.policy is not None:
                self._publish()
        else:
            self.version = self._published_version()
        self.started = True

    def _published_version(self) -> int:
        """The plugin may only accept rising versions, so keep counting if the checkpoint was lost."""
        try:
            with open(self.policy_path, encoding="utf-8") as source:
                return int(json.load(source)["version"])
        except (OSError, ValueError, KeyError, TypeError):
            return 0

    def _consume(self) -> bool:
        try:
            size = os.path.getsize(self.experience_path)
        except FileNotFoundError:
            return False
        if size < self.offset:
            log.warning("%s: experience file shrank from %d to %d, reading it again", self.name, self.offset, size)
            self.ledger.discard_open_fights()
            self.ledger.forget_ticks()
            self.offset = 0
            self.dirty = True
        if size == self.offset:
            return False
        consumed = False
        for line in read_complete_lines(self.experience_path, self.offset, self.hp.chunk_bytes):
            self.ledger.feed_line(line)
            self.offset += len(line) + 1
            consumed = True
            if self.ledger.ready_steps >= self.hp.batch_steps:
                break
        self.dirty = self.dirty or consumed
        return consumed

    def _initialise_networks(self) -> None:
        obs_dim = self.ledger.obs_dim
        action_dim = self.ledger.action_dim
        log.info("%s: new network, %d inputs, %d actions, %s reward", self.name, obs_dim, action_dim, self.profile.label)
        self.policy = Mlp([obs_dim, *HIDDEN_SIZES, action_dim], self.rng, POLICY_OUTPUT_GAIN)
        self.value = Mlp([obs_dim, *HIDDEN_SIZES, 1], self.rng, VALUE_OUTPUT_GAIN)
        self.policy_optimizer = Adam(self.policy.params)
        self.value_optimizer = Adam(self.value.params)
        self.version += 1
        self.dirty = True

    def _assemble(self, tracks: list[Track]) -> tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray, np.ndarray, list[int]]:
        obs = np.frombuffer(b"".join(t.obs.tobytes() for t in tracks), dtype=np.float32)
        mask = np.frombuffer(b"".join(t.mask.tobytes() for t in tracks), dtype=np.uint8)
        action = np.frombuffer(b"".join(t.action.tobytes() for t in tracks), dtype=np.uint16)
        logp = np.frombuffer(b"".join(t.logp.tobytes() for t in tracks), dtype=np.float64)
        rewards = np.frombuffer(b"".join(t.reward.tobytes() for t in tracks), dtype=np.float64)
        return (
            obs.reshape(-1, self.ledger.obs_dim).astype(np.float64),
            mask.reshape(-1, self.ledger.action_dim).astype(bool),
            action.astype(np.intp),
            logp,
            rewards,
            [len(t.action) for t in tracks],
        )

    def _update(self) -> None:
        tracks = self.ledger.take_ready()
        obs, mask, action, logp, rewards, lengths = self._assemble(tracks)
        self.total_steps += len(action)
        self.dirty = True
        self._warn_if_obs_unscaled(obs)
        values = self.value.forward(obs)[0][:, 0]
        advantages, returns = compute_gae(rewards, values, lengths, self.hp.gamma, self.hp.gae_lambda)
        full = Batch(obs, mask, action, logp, advantages, returns)

        # Behaviour steps too far from the current policy are dropped; the rest rely on the clip.
        drift = np.abs(action_log_probability(self.policy, full) - logp)
        fresh = drift <= self.hp.max_logp_drift
        stale_fraction = 1.0 - float(np.mean(fresh))
        explained = 1.0 - float(np.var(returns - values)) / max(float(np.var(returns)), 1e-8)
        stats: dict[str, float] = {}
        if int(fresh.sum()) >= self.hp.minibatch:
            backup = copy.deepcopy((self.policy, self.value, self.policy_optimizer, self.value_optimizer))
            stats = ppo_update(
                self.policy,
                self.value,
                self.policy_optimizer,
                self.value_optimizer,
                full.select(np.flatnonzero(fresh)),
                self.hp,
                self.rng,
            )
            if not all(np.isfinite(p).all() for net in (self.policy, self.value) for p in net.params):
                self.policy, self.value, self.policy_optimizer, self.value_optimizer = backup
                raise FloatingPointError(f"{self.name}: update produced non-finite weights, batch dropped")
            self.version += 1
        else:
            log.warning(
                "%s: %.0f%% of the batch is too far from the current policy, no update. "
                "Check that the plugin is loading policy.json and computing logp the same way",
                self.name,
                100.0 * stale_fraction,
            )
        self.save_checkpoint()
        if stats:
            self._publish()
        self._record(stats, float(rewards.sum()) / len(lengths), stale_fraction, explained)

    def _warn_if_obs_unscaled(self, obs: np.ndarray) -> None:
        largest = float(np.max(np.abs(obs)))
        if largest > LARGE_OBSERVATION and not self.warned_large_obs:
            self.warned_large_obs = True
            log.warning("%s: observations reach %.1f, tanh units will saturate unless the plugin scales them", self.name, largest)

    def _record(self, stats: dict[str, float], mean_reward: float, stale_fraction: float, explained: float) -> None:
        wins = self.ledger.recent_mob_wins
        win_share = sum(wins) / len(wins) if wins else 0.0
        nan = float("nan")
        row = {
            "time": time.strftime("%Y-%m-%d %H:%M:%S"),
            "network": self.name,
            "version": self.version,
            "total_steps": self.total_steps,
            "fights": self.ledger.finished_fights,
            "mob_wins_last50": f"{win_share:.3f}",
            "mean_reward": f"{mean_reward:.4f}",
            "policy_loss": f"{stats.get('policy_loss', nan):.5f}",
            "value_loss": f"{stats.get('value_loss', nan):.5f}",
            "entropy": f"{stats.get('entropy', nan):.4f}",
            "approx_kl": f"{stats.get('approx_kl', nan):.5f}",
            "clip_fraction": f"{stats.get('clip_fraction', nan):.3f}",
            "stale_fraction": f"{stale_fraction:.3f}",
            "explained_variance": f"{explained:.3f}",
            "discarded_fights": self.ledger.discarded_fights,
            "invalid_lines": self.ledger.invalid_lines,
        }
        self.metrics.write(row)
        log.info(
            "%s v%d steps=%d fights=%d wins50=%s reward=%s ploss=%s vloss=%s entropy=%s stale=%s",
            self.name,
            self.version,
            self.total_steps,
            self.ledger.finished_fights,
            row["mob_wins_last50"],
            row["mean_reward"],
            row["policy_loss"],
            row["value_loss"],
            row["entropy"],
            row["stale_fraction"],
        )

    def _publish(self) -> None:
        temporary = self.policy_path + ".tmp"
        try:
            with open(temporary, "w", encoding="utf-8") as target:
                json.dump({"version": self.version, "layers": self.policy.layers_json()}, target)
                target.flush()
                os.fsync(target.fileno())
            replace_with_retry(temporary, self.policy_path)
        except OSError as error:
            self.publish_pending = True
            log.warning("%s: could not publish version %d yet: %s", self.name, self.version, error)
            return
        self.publish_pending = False
        self._snapshot_if_due()

    def _snapshot_if_due(self) -> None:
        now = time.monotonic()
        if self.last_snapshot_time is not None and now - self.last_snapshot_time < self.hp.snapshot_seconds:
            return
        directory = os.path.join(self.directory, SNAPSHOT_DIR)
        os.makedirs(directory, exist_ok=True)
        shutil.copyfile(self.policy_path, os.path.join(directory, f"policy_v{self.version:06d}.json"))
        self.last_snapshot_time = now

    def save_checkpoint(self) -> None:
        if not self.started or (self.policy is None and self.offset == 0):
            return
        ledger_meta, ledger_arrays = self.ledger.state()
        meta = {
            "offset": self.offset,
            "version": self.version,
            "total_steps": self.total_steps,
            "rng": self.rng.bit_generator.state,
            "ledger": ledger_meta,
            "policy_adam_count": self.policy_optimizer.count if self.policy_optimizer else 0,
            "value_adam_count": self.value_optimizer.count if self.value_optimizer else 0,
        }
        arrays = dict(ledger_arrays)
        arrays["meta"] = np.array(json.dumps(meta))
        if self.policy is not None:
            for prefix, net, optimizer in (
                ("policy", self.policy, self.policy_optimizer),
                ("value", self.value, self.value_optimizer),
            ):
                for index, parameter in enumerate(net.params):
                    arrays[f"{prefix}_param_{index}"] = parameter
                    arrays[f"{prefix}_first_{index}"] = optimizer.first[index]
                    arrays[f"{prefix}_second_{index}"] = optimizer.second[index]
        temporary = self.checkpoint_path + ".tmp"
        with open(temporary, "wb") as target:
            np.savez(target, **arrays)
            target.flush()
            os.fsync(target.fileno())
        replace_with_retry(temporary, self.checkpoint_path)
        self.dirty = False
        self.last_checkpoint_time = time.monotonic()

    def _load_checkpoint(self) -> None:
        with np.load(self.checkpoint_path, allow_pickle=False) as stored:
            arrays = {key: stored[key] for key in stored.files}
        meta = json.loads(str(arrays["meta"]))
        self.offset = meta["offset"]
        self.version = meta["version"]
        self.total_steps = meta["total_steps"]
        self.rng.bit_generator.state = meta["rng"]
        self.ledger.restore(meta["ledger"], arrays)
        if "policy_param_0" not in arrays:
            return
        obs_dim = self.ledger.obs_dim
        action_dim = self.ledger.action_dim
        self.policy = Mlp([obs_dim, *HIDDEN_SIZES, action_dim], self.rng, POLICY_OUTPUT_GAIN)
        self.value = Mlp([obs_dim, *HIDDEN_SIZES, 1], self.rng, VALUE_OUTPUT_GAIN)
        self.rng.bit_generator.state = meta["rng"]
        self.policy_optimizer = Adam(self.policy.params)
        self.value_optimizer = Adam(self.value.params)
        for prefix, net, optimizer in (
            ("policy", self.policy, self.policy_optimizer),
            ("value", self.value, self.value_optimizer),
        ):
            for index, parameter in enumerate(net.params):
                parameter[...] = arrays[f"{prefix}_param_{index}"]
                optimizer.first[index][...] = arrays[f"{prefix}_first_{index}"]
                optimizer.second[index][...] = arrays[f"{prefix}_second_{index}"]
        self.policy_optimizer.count = meta["policy_adam_count"]
        self.value_optimizer.count = meta["value_adam_count"]
        log.info("%s: resumed at version %d, offset %d", self.name, self.version, self.offset)


class Trainer:
    def __init__(self, root: str, hp: Hyperparams, max_networks: int):
        self.root = root
        self.hp = hp
        self.max_networks = max_networks
        self.metrics = MetricsLog(os.path.join(root, METRICS_FILE))
        self.networks: dict[str, NetworkTrainer] = {}
        self.warned_about_limit = False

    def scan(self) -> None:
        names = sorted(
            entry.name
            for entry in os.scandir(self.root)
            if entry.is_dir()
            and (
                os.path.exists(os.path.join(entry.path, EXPERIENCE_FILE))
                or os.path.exists(os.path.join(entry.path, CHECKPOINT_FILE))
            )
        )
        for name in names:
            if name in self.networks:
                continue
            if len(self.networks) >= self.max_networks:
                if not self.warned_about_limit:
                    self.warned_about_limit = True
                    log.warning("more than %d networks in %s, ignoring the rest", self.max_networks, self.root)
                break
            self.networks[name] = NetworkTrainer(self.root, name, self.hp, self.metrics)

    def run_pass(self, stop: threading.Event) -> bool:
        worked = False
        for network in self.networks.values():
            if stop.is_set():
                break
            if time.monotonic() < network.retry_at:
                continue
            try:
                worked = network.poll() or worked
                network.failures = 0
            except Exception:
                network.failures += 1
                delay = min(MAX_BACKOFF_SECONDS, BASE_BACKOFF_SECONDS * 2**network.failures)
                network.retry_at = time.monotonic() + delay
                log.exception("%s: failed, trying again in %.0f s", network.name, delay)
        return worked

    def save_all(self) -> None:
        for network in self.networks.values():
            if not network.dirty:
                continue
            try:
                network.save_checkpoint()
            except Exception:
                log.exception("%s: could not save the checkpoint", network.name)

    def run(self, stop: threading.Event, poll_seconds: float, scan_seconds: float, once: bool) -> None:
        last_scan = -math.inf
        while not stop.is_set():
            if time.monotonic() - last_scan >= scan_seconds:
                self.scan()
                last_scan = time.monotonic()
            worked = self.run_pass(stop)
            if not worked:
                if once:
                    break
                stop.wait(poll_seconds)
        self.save_all()


def lower_process_priority() -> None:
    try:
        if sys.platform == "win32":
            below_normal_priority_class = 0x00004000
            kernel32 = ctypes.windll.kernel32
            kernel32.GetCurrentProcess.restype = ctypes.c_void_p
            kernel32.SetPriorityClass.argtypes = [ctypes.c_void_p, ctypes.c_uint32]
            kernel32.SetPriorityClass(kernel32.GetCurrentProcess(), below_normal_priority_class)
        else:
            os.nice(10)
    except (OSError, AttributeError) as error:
        log.warning("could not lower the process priority: %s", error)


def parse_arguments(argv: list[str] | None = None) -> argparse.Namespace:
    defaults = Hyperparams()
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("--root", required=True, help="folder with one subfolder per network")
    parser.add_argument("--batch-steps", type=int, default=defaults.batch_steps, help="steps per update")
    parser.add_argument("--minibatch", type=int, default=defaults.minibatch)
    parser.add_argument("--epochs", type=int, default=defaults.epochs)
    parser.add_argument("--learning-rate", type=float, default=defaults.learning_rate)
    parser.add_argument("--value-learning-rate", type=float, default=defaults.value_learning_rate)
    parser.add_argument("--gamma", type=float, default=defaults.gamma, help="discount per step (10 ticks)")
    parser.add_argument("--gae-lambda", type=float, default=defaults.gae_lambda)
    parser.add_argument("--clip", type=float, default=defaults.clip)
    parser.add_argument("--entropy", type=float, default=defaults.entropy)
    parser.add_argument("--max-grad-norm", type=float, default=defaults.max_grad_norm)
    parser.add_argument("--target-kl", type=float, default=defaults.target_kl, help="stops policy epochs past this")
    parser.add_argument("--max-logp-drift", type=float, default=defaults.max_logp_drift, help="steps further than this from the current policy are dropped")
    parser.add_argument("--fight-timeout-ticks", type=int, default=defaults.fight_timeout_ticks)
    parser.add_argument("--chunk-bytes", type=int, default=defaults.chunk_bytes)
    parser.add_argument("--checkpoint-seconds", type=float, default=defaults.checkpoint_seconds)
    parser.add_argument("--snapshot-seconds", type=float, default=defaults.snapshot_seconds)
    parser.add_argument("--seed", type=int, default=defaults.seed)
    parser.add_argument("--poll-seconds", type=float, default=5.0, help="sleep when there is no new data")
    parser.add_argument("--scan-seconds", type=float, default=30.0, help="how often to look for new networks")
    parser.add_argument("--max-networks", type=int, default=16)
    parser.add_argument("--once", action="store_true", help="process what is there and exit")
    return parser.parse_args(argv)


def configure_logging(root: str) -> None:
    formatter = logging.Formatter("%(asctime)s %(levelname)s %(message)s")
    console = logging.StreamHandler(sys.stdout)
    console.setFormatter(formatter)
    logfile = logging.handlers.RotatingFileHandler(
        os.path.join(root, LOG_FILE), maxBytes=LOG_BYTES, backupCount=LOG_BACKUPS, encoding="utf-8"
    )
    logfile.setFormatter(formatter)
    log.addHandler(console)
    log.addHandler(logfile)
    log.setLevel(logging.INFO)


def main(argv: list[str] | None = None) -> int:
    arguments = parse_arguments(argv)
    os.makedirs(arguments.root, exist_ok=True)
    configure_logging(arguments.root)
    lower_process_priority()
    hp = Hyperparams(
        batch_steps=arguments.batch_steps,
        minibatch=arguments.minibatch,
        epochs=arguments.epochs,
        learning_rate=arguments.learning_rate,
        value_learning_rate=arguments.value_learning_rate,
        gamma=arguments.gamma,
        gae_lambda=arguments.gae_lambda,
        clip=arguments.clip,
        entropy=arguments.entropy,
        max_grad_norm=arguments.max_grad_norm,
        target_kl=arguments.target_kl,
        max_logp_drift=arguments.max_logp_drift,
        fight_timeout_ticks=arguments.fight_timeout_ticks,
        chunk_bytes=arguments.chunk_bytes,
        checkpoint_seconds=arguments.checkpoint_seconds,
        snapshot_seconds=arguments.snapshot_seconds,
        seed=arguments.seed,
    )
    stop = threading.Event()

    def request_stop(_signal: int, _frame: object) -> None:
        stop.set()

    for name in ("SIGINT", "SIGTERM", "SIGBREAK"):
        if hasattr(signal, name):
            signal.signal(getattr(signal, name), request_stop)
    log.info("training from %s", os.path.abspath(arguments.root))
    Trainer(arguments.root, hp, arguments.max_networks).run(
        stop, arguments.poll_seconds, arguments.scan_seconds, arguments.once
    )
    log.info("stopped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
