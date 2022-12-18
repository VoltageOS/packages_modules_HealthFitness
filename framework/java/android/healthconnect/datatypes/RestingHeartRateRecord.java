/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.healthconnect.datatypes;

import android.annotation.IntRange;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Captures the user's resting heart rate. Each record represents a single instantaneous
 * measurement.
 */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_RESTING_HEART_RATE)
public final class RestingHeartRateRecord extends InstantRecord {

    private final long mBeatsPerMinute;

    /**
     * @param metadata Metadata to be associated with the record. See {@link Metadata}.
     * @param time Start time of this activity
     * @param zoneOffset Zone offset of the user when the activity started
     * @param beatsPerMinute BeatsPerMinute of this activity
     */
    private RestingHeartRateRecord(
            @NonNull Metadata metadata,
            @NonNull Instant time,
            @NonNull ZoneOffset zoneOffset,
            @NonNull long beatsPerMinute) {
        super(metadata, time, zoneOffset);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(time);
        Objects.requireNonNull(zoneOffset);
        mBeatsPerMinute = beatsPerMinute;
    }
    /**
     * @return beatsPerMinute
     */
    public long getBeatsPerMinute() {
        return mBeatsPerMinute;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     *
     * @param o the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        RestingHeartRateRecord that = (RestingHeartRateRecord) o;
        return getBeatsPerMinute() == that.getBeatsPerMinute();
    }

    /** Returns a hash code value for the object. */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getBeatsPerMinute());
    }

    /** Builder class for {@link RestingHeartRateRecord} */
    public static final class Builder {
        private final Metadata mMetadata;
        private final Instant mTime;
        private ZoneOffset mZoneOffset;
        private final long mBeatsPerMinute;

        /**
         * @param metadata Metadata to be associated with the record. See {@link Metadata}.
         * @param time Start time of this activity
         * @param beatsPerMinute Heart beats per minute. Required field. Validation range: 1-300.
         */
        public Builder(
                @NonNull Metadata metadata,
                @NonNull Instant time,
                @IntRange(from = 1, to = 300) long beatsPerMinute) {
            Objects.requireNonNull(metadata);
            Objects.requireNonNull(time);
            mMetadata = metadata;
            mTime = time;
            mZoneOffset = ZoneOffset.systemDefault().getRules().getOffset(Instant.now());
            mBeatsPerMinute = beatsPerMinute;
        }

        /** Sets the zone offset of the user when the activity happened */
        @NonNull
        public Builder setZoneOffset(@NonNull ZoneOffset zoneOffset) {
            Objects.requireNonNull(zoneOffset);
            mZoneOffset = zoneOffset;
            return this;
        }

        /**
         * @return Object of {@link RestingHeartRateRecord}
         */
        @NonNull
        public RestingHeartRateRecord build() {
            return new RestingHeartRateRecord(mMetadata, mTime, mZoneOffset, mBeatsPerMinute);
        }
    }
}