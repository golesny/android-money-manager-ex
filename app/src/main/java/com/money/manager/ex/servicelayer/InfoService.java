/*
 * Copyright (C) 2012-2018 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.money.manager.ex.servicelayer;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
//import net.sqlcipher.database.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.money.manager.ex.MmexApplication;
import com.money.manager.ex.datalayer.InfoRepositorySql;
import com.money.manager.ex.datalayer.Select;
import com.money.manager.ex.domainmodel.Info;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Access and manipulation of the info in the Info Table
 */
public class InfoService
    extends ServiceBase { // from master repo
    String[] defaultColor = new String[]{"246,144,144", "229,196,146", "245,237,149",
            "186,226,185", "135,190,219", "172,167,239", "212,138,215"};

    public InfoService(Context context) {
        super(context);

        MmexApplication.getApp().iocComponent.inject(this);
    }

    @Inject
    public InfoRepositorySql repository;

    public long insertRaw(SupportSQLiteDatabase db, String key, Long value) {
        ContentValues values = new ContentValues();

        values.put(Info.INFONAME, key);
        values.put(Info.INFOVALUE, value);

        return db.insert(InfoRepositorySql.TABLE_NAME, CONFLICT_REPLACE, values);
    }

    public long insertRaw(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();

        values.put(Info.INFONAME, key);
        values.put(Info.INFOVALUE, value);

        return db.insert(InfoRepositorySql.TABLE_NAME, null, values);
    }

    /**
     * Update the values via direct access to the database.
     * @param db        Database to use
     * @param recordId  Id of the info record. Required for the update statement.
     * @param key       Info Name
     * @param value     Info Value
     * @return the number of rows affected
     */
    public long updateRaw(SupportSQLiteDatabase db, long recordId, String key, Long value) {
        ContentValues values = new ContentValues();
        values.put(Info.INFONAME, key);
        values.put(Info.INFOVALUE, value);

        return db.update(InfoRepositorySql.TABLE_NAME,
                CONFLICT_REPLACE,
                values,
            Info.INFOID + "=?",
                new String[] { Long.toString(recordId)}
        );
    }

    public long updateRaw(SupportSQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put(Info.INFONAME, key);
        values.put(Info.INFOVALUE, value);

        return db.update(InfoRepositorySql.TABLE_NAME, CONFLICT_REPLACE, values,
            Info.INFONAME + "=?",
                new String[] { key });
    }

    /**
     * Retrieve value of info
     * @param info to be retrieve
     * @return value
     */
    public String getInfoValue(String info) {
        Cursor cursor;
        String ret = null;

        try {
            Select query = new Select()
                    .from(InfoRepositorySql.TABLE_NAME)
                    .where(Info.INFONAME + "=?", info);
            cursor = repository.query(query);
            if (cursor == null) return null;

            if (cursor.moveToFirst()) {
                ret = cursor.getString(cursor.getColumnIndexOrThrow(Info.INFOVALUE));
            }
            cursor.close();
        } catch (Exception e) {
            Timber.e(e, "retrieving info value: %s", info);
        }

        return ret;
    }
    /**
     * Retrieve value of info key
     * @param key to be retrieve
     * @param value of default if fetch nothing
     * @return value
     */
    public String getInfoValue(String key, String value) {
        String ret = getInfoValue(key);
        if (ret == null)
            ret = value;
        return ret;
    }

    /**
     * Update value of info.
     * @param key to update
     * @param value value to be used
     * @return true if update success otherwise false
     */
    public boolean setInfoValue(String key, String value) {
        boolean result = false;
        // check if info exists
        boolean exists = (getInfoValue(key) != null);

        Info entity = Info.create(key, value);

        try {
            if (exists) {
                result = repository.update(entity);
            } else {
                long id = repository.insert(entity);
                result = id > 0;
            }
        } catch (Exception e) {
            Timber.e(e, "writing info value");
        }

        return result;
    }

    public int getColorNumberFromInfoKey(int n) {
        String[] colors = getColorArrayFromInfoKey(n);
        if ( colors == null ) return Color.TRANSPARENT;
        return Color.rgb(Integer.parseInt(colors[0]), Integer.parseInt(colors[1]), Integer.parseInt(colors[2]));
    }

    public String[] getColorArrayFromInfoKey(int n) {
        if ( n <= 0 || n > defaultColor.length ) { // fix issue #2528
            return null;
        }
        String colorList = getInfoValue(String.format("USER_COLOR%d",n),"");
        if ( colorList.isEmpty() ) {
            colorList = defaultColor[n-1];
        }
        return colorList.split(",");
    }

}
