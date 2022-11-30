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

package android.healthconnect.aidl;

import android.healthconnect.ChangeLogTokenRequest;
import android.healthconnect.datatypes.Record;
import android.healthconnect.internal.datatypes.utils.RecordMapper;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Parcel class for {@link ChangeLogTokenRequest}
 *
 * @hide
 */
public class ChangeLogTokenRequestParcel implements Parcelable {
    public static final Creator<ChangeLogTokenRequestParcel> CREATOR =
            new Creator<ChangeLogTokenRequestParcel>() {
                @Override
                public ChangeLogTokenRequestParcel createFromParcel(@NonNull Parcel in) {
                    return new ChangeLogTokenRequestParcel(in);
                }

                @Override
                public ChangeLogTokenRequestParcel[] newArray(int size) {
                    return new ChangeLogTokenRequestParcel[size];
                }
            };
    private final int[] mRecordTypes;
    private final List<String> mPackageNamesToFilter;

    public ChangeLogTokenRequestParcel(@NonNull ChangeLogTokenRequest request) {
        mRecordTypes = new int[request.getRecordTypes().size()];
        int index = 0;
        for (Class<? extends Record> recordClass : request.getRecordTypes()) {
            mRecordTypes[index++] = RecordMapper.getInstance().getRecordType(recordClass);
        }
        mPackageNamesToFilter = new ArrayList<>(request.getDataOriginFilters().size());
        request.getDataOriginFilters()
                .forEach((dataOrigin) -> mPackageNamesToFilter.add(dataOrigin.getPackageName()));
    }

    protected ChangeLogTokenRequestParcel(@NonNull Parcel in) {
        mRecordTypes = in.createIntArray();
        mPackageNamesToFilter = in.createStringArrayList();
    }

    @NonNull
    public int[] getRecordTypes() {
        return mRecordTypes;
    }

    @NonNull
    public List<String> getPackageNamesToFilter() {
        return mPackageNamesToFilter;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeIntArray(mRecordTypes);
        dest.writeStringList(mPackageNamesToFilter);
    }
}