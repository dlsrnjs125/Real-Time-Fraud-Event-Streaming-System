#!/usr/bin/env python3
"""Verify Phase 3 partition-affinity workloads do not create hot-user pressure."""

from __future__ import annotations

import json
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


WORKLOAD_DIR = Path(__file__).parents[2] / "load-test" / "workloads" / "v3"
MANIFESTS = [
    WORKLOAD_DIR / "partition-balanced-v1.json",
    WORKLOAD_DIR / "partition-skew-hot-p2-v1.json",
]
PARTITION_CYCLE_LENGTH = 600


class VerificationError(ValueError):
    pass


def deterministic_value(seed: int, vu: int, iteration: int) -> int:
    value = (seed + int32(vu * 0x9E3779B1) + int32(iteration * 0x85EBCA6B)) & 0xFFFFFFFF
    value = int32((value ^ (value >> 16)) * 0x7FEB352D)
    value = int32((value ^ (value >> 15)) * 0x846CA68B)
    return (value ^ (value >> 16)) & 0xFFFFFFFF


def int32(value: int) -> int:
    return value & 0xFFFFFFFF


def build_share_counts(distribution: list[tuple[int, float]], total: int) -> list[int]:
    counts = [int(share * total) for _, share in distribution]
    remaining = total - sum(counts)
    remainders = sorted(
        ((index, share * total - int(share * total)) for index, (_, share) in enumerate(distribution)),
        key=lambda item: (-item[1], item[0]),
    )
    index = 0
    while remaining > 0:
        counts[remainders[index % len(remainders)][0]] += 1
        remaining -= 1
        index += 1
    return counts


def build_partition_cycle(distribution: list[tuple[int, float]], total: int, seed: int) -> list[int]:
    counts = build_share_counts(distribution, total)
    sequence: list[int] = []
    for index, (partition, _) in enumerate(distribution):
        sequence.extend([partition] * counts[index])
    for index in range(len(sequence) - 1, 0, -1):
        swap_index = deterministic_value(seed, index, counts[index % len(counts)]) % (index + 1)
        sequence[index], sequence[swap_index] = sequence[swap_index], sequence[index]
    return sequence


def murmur2_ascii(value: str) -> int:
    data = value.encode("ascii")
    seed = 0x9747B28C
    m = 0x5BD1E995
    r = 24
    h = (seed ^ len(data)) & 0xFFFFFFFF
    length = len(data)
    index = 0

    while length >= 4:
        k = (
            data[index]
            | (data[index + 1] << 8)
            | (data[index + 2] << 16)
            | (data[index + 3] << 24)
        )
        k = int32(k * m)
        k ^= k >> r
        k = int32(k * m)
        h = int32(h * m)
        h ^= k
        index += 4
        length -= 4

    if length == 3:
        h ^= data[index + 2] << 16
    if length >= 2:
        h ^= data[index + 1] << 8
    if length >= 1:
        h ^= data[index]
        h = int32(h * m)

    h ^= h >> 13
    h = int32(h * m)
    h ^= h >> 15
    return h & 0xFFFFFFFF


def partition_for_key(key: str, partition_count: int) -> int:
    return (murmur2_ascii(key) & 0x7FFFFFFF) % partition_count


def find_users_for_partition(workload_id: str, target_partition: int, count: int, partition_count: int) -> list[str]:
    users: list[str] = []
    candidate = 0
    while len(users) < count:
        user_id = f"v3-phase3-{workload_id}-p{target_partition}-user-{candidate}"
        if partition_for_key(user_id, partition_count) == target_partition:
            users.append(user_id)
        candidate += 1
        if candidate > count * partition_count * 30 + 1000:
            raise VerificationError(f"could not build enough users for partition {target_partition}")
    return users


def verify_manifest(path: Path) -> None:
    manifest = json.loads(path.read_text(encoding="utf-8"))
    distribution = sorted((int(partition), float(share)) for partition, share in manifest["targetPartitionDistribution"].items())
    partition_count = len(distribution)
    partition_cycle = build_partition_cycle(distribution, PARTITION_CYCLE_LENGTH, int(manifest["randomSeed"]))
    user_counts = build_share_counts(distribution, int(manifest["userCardinality"]))
    user_pools = {
        partition: find_users_for_partition(manifest["workloadId"], partition, user_counts[index], partition_count)
        for index, (partition, _) in enumerate(distribution)
    }
    partition_occurrences = {partition: 0 for partition, _ in distribution}
    events_by_user: Counter[str] = Counter()
    users_by_partition: dict[int, set[str]] = defaultdict(set)
    partition_events: Counter[int] = Counter()

    for iteration in range(int(manifest["eventLimit"])):
        partition = partition_cycle[iteration % len(partition_cycle)]
        pool = user_pools[partition]
        occurrence = partition_occurrences[partition]
        user_id = pool[occurrence % len(pool)]
        partition_occurrences[partition] += 1
        events_by_user[user_id] += 1
        users_by_partition[partition].add(user_id)
        partition_events[partition] += 1

    if len(events_by_user) != manifest["userCardinality"]:
        raise VerificationError(f"{path.name}: generated unique users drifted: {len(events_by_user)}")

    event_counts = sorted(events_by_user.values())
    if event_counts[0] != event_counts[-1]:
        raise VerificationError(f"{path.name}: events/user is not balanced: min={event_counts[0]} max={event_counts[-1]}")

    top_user_share = event_counts[-1] / manifest["eventLimit"]
    expected_top_user_share = 1 / manifest["userCardinality"]
    if abs(top_user_share - expected_top_user_share) > 0.000001:
        raise VerificationError(f"{path.name}: top user share drifted: {top_user_share}")

    for index, (partition, _) in enumerate(distribution):
        if len(users_by_partition[partition]) != user_counts[index]:
            raise VerificationError(f"{path.name}: partition {partition} unique users drifted")

    print(
        f"PASS: {path.name}: users={len(events_by_user)}, "
        f"eventsPerUser={event_counts[-1]}, topUserShare={top_user_share:.6f}, "
        f"partitionEvents={dict(sorted(partition_events.items()))}"
    )


def main() -> int:
    try:
        for manifest_path in MANIFESTS:
            verify_manifest(manifest_path)
    except (OSError, json.JSONDecodeError, VerificationError) as exc:
        print(f"ERROR: {exc}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
