#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <vector>

/**
 * Retains per-device recovery markers when a JNI batch cannot be delivered.
 * The next successful batch starts with SYN_DROPPED for every device represented by the lost
 * batch, so the Kotlin consumer releases only the affected physical state before newer events.
 * This header intentionally has no JNI or Android dependency and is host-testable.
 */
class EvdevBatchRecovery {
public:
    static constexpr std::size_t kRecordFields = 6;
    static constexpr std::int64_t kEvSyn = 0;
    static constexpr std::int64_t kSynDropped = 3;

    struct Device {
        std::int64_t deviceClass;
        std::int64_t deviceId;
    };

    template <typename Long>
    void markDroppedBatch(const std::vector<Long>& records) {
        for (std::size_t i = 0; i + kRecordFields <= records.size(); i += kRecordFields) {
            const auto deviceClass = records[i];
            const auto deviceId = records[i + 4];
            if (deviceClass <= 0) continue;
            auto found = std::find_if(pending_.begin(), pending_.end(), [&](const Device& item) {
                return item.deviceId == deviceId;
            });
            if (found == pending_.end()) {
                pending_.push_back({deviceClass, deviceId});
            } else {
                found->deviceClass |= deviceClass;
            }
        }
    }

    /** Prepends recovery records and preserves the order of the actual new batch. */
    template <typename Long>
    void prependMarkers(std::vector<Long>& records) const {
        if (pending_.empty()) return;
        std::vector<Long> combined;
        combined.reserve(pending_.size() * kRecordFields + records.size());
        for (const auto& device : pending_) {
            combined.insert(combined.end(), {
                static_cast<Long>(device.deviceClass), static_cast<Long>(kEvSyn),
                static_cast<Long>(kSynDropped), static_cast<Long>(1),
                static_cast<Long>(device.deviceId), static_cast<Long>(0)
            });
        }
        combined.insert(combined.end(), records.begin(), records.end());
        records.swap(combined);
    }

    void markDelivered() { pending_.clear(); }
    bool hasPending() const { return !pending_.empty(); }
    std::size_t pendingDeviceCount() const { return pending_.size(); }

private:
    std::vector<Device> pending_;
};
