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
package android.healthconnect.internal.datatypes;

import android.healthconnect.datatypes.BodyTemperatureMeasurementLocation;
import android.healthconnect.datatypes.BodyTemperatureRecord;
import android.healthconnect.datatypes.Identifier;
import android.healthconnect.datatypes.RecordTypeIdentifier;
import android.healthconnect.datatypes.units.Temperature;
import android.os.Parcel;

import androidx.annotation.NonNull;

/**
 * @see BodyTemperatureRecord
 * @hide
 */
@Identifier(recordIdentifier = RecordTypeIdentifier.RECORD_TYPE_BODY_TEMPERATURE)
public final class BodyTemperatureRecordInternal
        extends InstantRecordInternal<BodyTemperatureRecord> {
    private int mMeasurementLocation;
    private double mTemperature;

    @BodyTemperatureMeasurementLocation.BodyTemperatureMeasurementLocations
    public int getMeasurementLocation() {
        return mMeasurementLocation;
    }

    /** returns this object with the specified measurementLocation */
    @NonNull
    public BodyTemperatureRecordInternal setMeasurementLocation(int measurementLocation) {
        this.mMeasurementLocation = measurementLocation;
        return this;
    }

    public double getTemperature() {
        return mTemperature;
    }

    /** returns this object with the specified temperature */
    @NonNull
    public BodyTemperatureRecordInternal setTemperature(double temperature) {
        this.mTemperature = temperature;
        return this;
    }

    @NonNull
    @Override
    public BodyTemperatureRecord toExternalRecord() {
        return new BodyTemperatureRecord.Builder(
                        buildMetaData(),
                        getTime(),
                        getMeasurementLocation(),
                        Temperature.fromCelsius(getTemperature()))
                .setZoneOffset(getZoneOffset())
                .build();
    }

    @Override
    void populateInstantRecordFrom(@NonNull Parcel parcel) {
        mMeasurementLocation = parcel.readInt();
        mTemperature = parcel.readDouble();
    }

    @Override
    void populateInstantRecordFrom(@NonNull BodyTemperatureRecord bodyTemperatureRecord) {
        mMeasurementLocation = bodyTemperatureRecord.getMeasurementLocation();
        mTemperature = bodyTemperatureRecord.getTemperature().getInCelsius();
    }

    @Override
    void populateInstantRecordTo(@NonNull Parcel parcel) {
        parcel.writeInt(mMeasurementLocation);
        parcel.writeDouble(mTemperature);
    }
}
