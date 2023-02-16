/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.health.connect.ratelimiter;

import android.annotation.IntDef;
import android.health.connect.HealthConnectException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Basic rate limiter that assigns a fixed request rate quota. If no quota has previously been noted
 * (e.g. first request scenario), the full quota for each window will be immediately granted.
 *
 * @hide
 */
public final class RateLimiter {
    // The maximum number of bytes a client can insert in one go.
    private static final String CHUNK_SIZE_LIMIT_IN_BYTES = "chunk_size_limit_in_bytes";
    // The maximum size in bytes of a single record a client can insert in one go.
    private static final String RECORD_SIZE_LIMIT_IN_BYTES = "record_size_limit_in_bytes";
    private static final float DEFAULT_QUOTA_BUCKET_PER_15M_FOREGROUND_LIMIT_VALUE = 1000f;
    private static final float DEFAULT_QUOTA_BUCKET_PER_24H_FOREGROUND_LIMIT_VALUE = 2000f;
    private static final float DEFAULT_QUOTA_BUCKET_PER_15M_BACKGROUND_LIMIT_VALUE = 300f;
    private static final float DEFAULT_QUOTA_BUCKET_PER_24h_BACKGROUND_LIMIT_VALUE = 600f;
    private static final int DEFAULT_CHUNK_SIZE_LIMIT_IN_BYTES_VALUE = 5000000;
    private static final int DEFAULT_RECORD_SIZE_LIMIT_IN_BYTES_VALUE = 1000000;
    private static final int DEFAULT_API_CALL_COST = 1;
    private static final Map<Integer, Map<Integer, Quota>> sUserIdToQuotasMap = new HashMap<>();

    private static final ConcurrentMap<Integer, Integer> sLocks = new ConcurrentHashMap<>();
    private static final Map<Integer, Float> QUOTA_BUCKET_TO_MAX_API_CALL_QUOTA_MAP =
            Map.of(
                    QuotaBucket.QUOTA_BUCKET_READS_PER_15M_FOREGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_15M_FOREGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_READS_PER_24H_FOREGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_24H_FOREGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_READS_PER_15M_BACKGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_15M_BACKGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_READS_PER_24H_BACKGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_24h_BACKGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_15M_FOREGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_24H_FOREGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_15M_BACKGROUND_LIMIT_VALUE,
                    QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND,
                    DEFAULT_QUOTA_BUCKET_PER_24h_BACKGROUND_LIMIT_VALUE);
    private static final Map<String, Integer> QUOTA_BUCKET_TO_MAX_MEMORY_QUOTA_MAP =
            Map.of(
                    CHUNK_SIZE_LIMIT_IN_BYTES,
                    DEFAULT_CHUNK_SIZE_LIMIT_IN_BYTES_VALUE,
                    RECORD_SIZE_LIMIT_IN_BYTES,
                    DEFAULT_RECORD_SIZE_LIMIT_IN_BYTES_VALUE);

    public static void tryAcquireApiCallQuota(
            int uid, @QuotaCategory.Type int quotaCategory, boolean isInForeground) {
        if (quotaCategory == QuotaCategory.QUOTA_CATEGORY_UNDEFINED) {
            throw new IllegalArgumentException("Quota category not defined.");
        }

        // Rate limiting not applicable.
        if (quotaCategory == QuotaCategory.QUOTA_CATEGORY_UNMETERED) {
            return;
        }
        synchronized (getLockObject(uid)) {
            spendResourcesIfAvailable(
                    uid,
                    getAffectedQuotaBuckets(quotaCategory, isInForeground),
                    DEFAULT_API_CALL_COST);
        }
    }

    public static void checkMaxChunkMemoryUsage(long memoryCost) {
        long memoryLimit = getConfiguredMaxApiMemoryQuota(CHUNK_SIZE_LIMIT_IN_BYTES);
        if (memoryCost > memoryLimit) {
            throw new HealthConnectException(
                    HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED,
                    "Records chunk size exceeded the max chunk limit: "
                            + memoryLimit
                            + ", was: "
                            + memoryCost);
        }
    }

    public static void checkMaxRecordMemoryUsage(long memoryCost) {
        long memoryLimit = getConfiguredMaxApiMemoryQuota(RECORD_SIZE_LIMIT_IN_BYTES);
        if (memoryCost > memoryLimit) {
            throw new HealthConnectException(
                    HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED,
                    "Record size exceeded the single record size limit: "
                            + memoryLimit
                            + ", was: "
                            + memoryCost);
        }
    }

    public static void clearCache() {
        sUserIdToQuotasMap.clear();
    }

    private static Object getLockObject(int uid) {
        sLocks.putIfAbsent(uid, uid);
        return sLocks.get(uid);
    }

    private static void spendResourcesIfAvailable(int uid, List<Integer> quotaBuckets, int cost) {
        Map<Integer, Float> quotaBucketToAvailableQuotaMap =
                quotaBuckets.stream()
                        .collect(
                                Collectors.toMap(
                                        quotaBucket -> quotaBucket,
                                        quotaBucket ->
                                                getAvailableQuota(
                                                        quotaBucket, getQuota(uid, quotaBucket))));
        for (@QuotaBucket.Type int quotaBucket : quotaBuckets) {
            hasSufficientQuota(quotaBucketToAvailableQuotaMap.get(quotaBucket), cost);
        }
        for (@QuotaBucket.Type int quotaBucket : quotaBuckets) {
            spendResources(uid, quotaBucket, quotaBucketToAvailableQuotaMap.get(quotaBucket), cost);
        }
    }

    private static void spendResources(
            int uid, @QuotaBucket.Type int quotaBucket, float availableQuota, int cost) {
        sUserIdToQuotasMap
                .get(uid)
                .put(quotaBucket, new Quota(Instant.now(), availableQuota - cost));
    }

    private static void hasSufficientQuota(float availableQuota, int cost) {
        if (availableQuota < cost) {
            throw new HealthConnectException(
                    HealthConnectException.ERROR_RATE_LIMIT_EXCEEDED,
                    "API call quota exceeded, availableQuota: "
                            + availableQuota
                            + " requested: "
                            + cost);
        }
    }

    private static float getAvailableQuota(@QuotaBucket.Type int quotaBucket, Quota quota) {
        Instant lastUpdatedTime = quota.getLastUpdatedTime();
        Instant currentTime = Instant.now();
        Duration timeSinceLastQuotaSpend = Duration.between(lastUpdatedTime, currentTime);
        Duration window = getWindowDuration(quotaBucket);
        float accumulated =
                timeSinceLastQuotaSpend.toMillis()
                        * (getConfiguredApiCallMaxQuota(quotaBucket) / (float) window.toMillis());
        // Cannot accumulate more than the configured max quota.
        return Math.min(
                quota.getRemainingQuota() + accumulated, getConfiguredApiCallMaxQuota(quotaBucket));
    }

    private static Quota getQuota(int uid, @QuotaBucket.Type int quotaBucket) {
        // Handles first request scenario.
        if (!sUserIdToQuotasMap.containsKey(uid)) {
            sUserIdToQuotasMap.put(uid, new HashMap<>());
        }
        Map<Integer, Quota> packageQuotas = sUserIdToQuotasMap.get(uid);
        Quota quota = packageQuotas.get(quotaBucket);
        if (quota == null) {
            quota = getInitialQuota(quotaBucket);
        }
        return quota;
    }

    private static Quota getInitialQuota(@QuotaBucket.Type int bucket) {
        return new Quota(Instant.now(), getConfiguredApiCallMaxQuota(bucket));
    }

    private static Duration getWindowDuration(@QuotaBucket.Type int quotaBucket) {
        switch (quotaBucket) {
            case QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND:
            case QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND:
            case QuotaBucket.QUOTA_BUCKET_READS_PER_24H_BACKGROUND:
            case QuotaBucket.QUOTA_BUCKET_READS_PER_24H_FOREGROUND:
                return Duration.ofHours(24);
            case QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND:
            case QuotaBucket.QUOTA_BUCKET_READS_PER_15M_FOREGROUND:
            case QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND:
            case QuotaBucket.QUOTA_BUCKET_READS_PER_15M_BACKGROUND:
                return Duration.ofMinutes(15);
            case QuotaBucket.QUOTA_BUCKET_UNDEFINED:
                throw new IllegalArgumentException("Invalid quota bucket.");
        }
        throw new IllegalArgumentException("Invalid quota bucket.");
    }

    private static float getConfiguredApiCallMaxQuota(@QuotaBucket.Type int quotaBucket) {
        if (!QUOTA_BUCKET_TO_MAX_API_CALL_QUOTA_MAP.containsKey(quotaBucket)) {
            throw new IllegalArgumentException(
                    "Max quota not found for quotaBucket: " + quotaBucket);
        }
        return QUOTA_BUCKET_TO_MAX_API_CALL_QUOTA_MAP.get(quotaBucket);
    }

    private static int getConfiguredMaxApiMemoryQuota(String quotaBucket) {
        if (!QUOTA_BUCKET_TO_MAX_MEMORY_QUOTA_MAP.containsKey(quotaBucket)) {
            throw new IllegalArgumentException(
                    "Max quota not found for quotaBucket: " + quotaBucket);
        }
        return QUOTA_BUCKET_TO_MAX_MEMORY_QUOTA_MAP.get(quotaBucket);
    }

    private static List<Integer> getAffectedQuotaBuckets(
            @QuotaCategory.Type int quotaCategory, boolean isInForeground) {
        switch (quotaCategory) {
            case QuotaCategory.QUOTA_CATEGORY_READ:
                if (isInForeground) {
                    return List.of(
                            QuotaBucket.QUOTA_BUCKET_READS_PER_15M_FOREGROUND,
                            QuotaBucket.QUOTA_BUCKET_READS_PER_24H_FOREGROUND);
                } else {
                    return List.of(
                            QuotaBucket.QUOTA_BUCKET_READS_PER_15M_BACKGROUND,
                            QuotaBucket.QUOTA_BUCKET_READS_PER_24H_BACKGROUND);
                }
            case QuotaCategory.QUOTA_CATEGORY_WRITE:
                if (isInForeground) {
                    return List.of(
                            QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND,
                            QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND);
                } else {
                    return List.of(
                            QuotaBucket.QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND,
                            QuotaBucket.QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND);
                }
            case QuotaCategory.QUOTA_CATEGORY_UNDEFINED:
            case QuotaCategory.QUOTA_CATEGORY_UNMETERED:
                throw new IllegalArgumentException("Invalid quota category.");
        }
        throw new IllegalArgumentException("Invalid quota category.");
    }

    public static final class QuotaBucket {
        public static final int QUOTA_BUCKET_UNDEFINED = 0;
        public static final int QUOTA_BUCKET_READS_PER_15M_FOREGROUND = 1;
        public static final int QUOTA_BUCKET_READS_PER_24H_FOREGROUND = 2;
        public static final int QUOTA_BUCKET_READS_PER_15M_BACKGROUND = 3;
        public static final int QUOTA_BUCKET_READS_PER_24H_BACKGROUND = 4;
        public static final int QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND = 5;
        public static final int QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND = 6;
        public static final int QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND = 7;
        public static final int QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND = 8;

        private QuotaBucket() {}

        /** @hide */
        @IntDef({
            QUOTA_BUCKET_UNDEFINED,
            QUOTA_BUCKET_READS_PER_15M_FOREGROUND,
            QUOTA_BUCKET_READS_PER_24H_FOREGROUND,
            QUOTA_BUCKET_READS_PER_15M_BACKGROUND,
            QUOTA_BUCKET_READS_PER_24H_BACKGROUND,
            QUOTA_BUCKET_WRITES_PER_15M_FOREGROUND,
            QUOTA_BUCKET_WRITES_PER_24H_FOREGROUND,
            QUOTA_BUCKET_WRITES_PER_15M_BACKGROUND,
            QUOTA_BUCKET_WRITES_PER_24H_BACKGROUND,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {}
    }

    public static final class QuotaCategory {
        public static final int QUOTA_CATEGORY_UNDEFINED = 0;
        public static final int QUOTA_CATEGORY_UNMETERED = 1;
        public static final int QUOTA_CATEGORY_READ = 2;
        public static final int QUOTA_CATEGORY_WRITE = 3;

        private QuotaCategory() {}

        /** @hide */
        @IntDef({
            QUOTA_CATEGORY_UNDEFINED,
            QUOTA_CATEGORY_UNMETERED,
            QUOTA_CATEGORY_READ,
            QUOTA_CATEGORY_WRITE,
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {}
    }
}