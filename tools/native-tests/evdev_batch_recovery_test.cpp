#include "../../app/src/main/cpp/evdev_batch_recovery.h"

#include <cassert>
#include <cstdint>
#include <vector>

static std::vector<std::int64_t> record(std::int64_t cls, std::int64_t type,
                                        std::int64_t code, std::int64_t value,
                                        std::int64_t id, std::int64_t time = 1) {
    return {cls, type, code, value, id, time};
}

int main() {
    EvdevBatchRecovery recovery;

    // One failed batch with repeated records from a single device creates one recovery marker.
    auto lost = record(1, 2, 0, 12, -101);
    const auto key = record(2, 1, 30, 1, -102);
    lost.insert(lost.end(), key.begin(), key.end());
    auto duplicate = record(1, 1, 272, 1, -101);
    lost.insert(lost.end(), duplicate.begin(), duplicate.end());
    recovery.markDroppedBatch(lost);
    assert(recovery.hasPending());
    assert(recovery.pendingDeviceCount() == 2);

    // Markers are prepended in device order, ahead of the new batch's actual records.
    auto next = record(1, 2, 0, -4, -101, 2);
    recovery.prependMarkers(next);
    assert(next.size() == 3 * EvdevBatchRecovery::kRecordFields);
    assert(next[0] == 1 && next[1] == 0 && next[2] == 3 && next[4] == -101);
    assert(next[6] == 2 && next[7] == 0 && next[8] == 3 && next[10] == -102);
    assert(next[12] == 1 && next[13] == 2 && next[14] == 0 && next[15] == -4);

    // A second failed delivery unions new IDs while retaining previous recovery obligations.
    recovery.markDroppedBatch(next);
    assert(recovery.pendingDeviceCount() == 2);
    auto another = record(1, 2, 1, 7, -103, 3);
    recovery.markDroppedBatch(another);
    assert(recovery.pendingDeviceCount() == 3);

    auto finalBatch = record(2, 1, 31, 0, -102, 4);
    recovery.prependMarkers(finalBatch);
    assert(finalBatch.size() == 4 * EvdevBatchRecovery::kRecordFields);
    assert(finalBatch[0] == 1 && finalBatch[4] == -101);
    assert(finalBatch[6] == 2 && finalBatch[10] == -102);
    assert(finalBatch[12] == 1 && finalBatch[16] == -103);

    recovery.markDelivered();
    assert(!recovery.hasPending());
    assert(recovery.pendingDeviceCount() == 0);

    // Empty payload still allows a pending marker-only recovery batch to be formed.
    recovery.markDroppedBatch(record(1, 1, 272, 1, -104));
    std::vector<std::int64_t> empty;
    recovery.prependMarkers(empty);
    assert(empty.size() == EvdevBatchRecovery::kRecordFields);
    assert(empty[0] == 1 && empty[1] == 0 && empty[2] == 3 && empty[4] == -104);

    return 0;
}
