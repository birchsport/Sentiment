package com.binarypoet.sentiment.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class StatusReaderDbHelper extends SQLiteOpenHelper {
    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "StatusReader.db";

    private static final String TEXT_TYPE = " TEXT";
    private static final String INTEGER_TYPE = " INTEGER";
    private static final String COMMA_SEP = ",";
    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + StatusReaderContract.StatusEntry.TABLE_NAME + " (" +
                    StatusReaderContract.StatusEntry._ID + " INTEGER PRIMARY KEY," +
                    StatusReaderContract.StatusEntry.COLUMN_NAME_ID + INTEGER_TYPE + COMMA_SEP +
                    StatusReaderContract.StatusEntry.COLUMN_NAME_SCORE + INTEGER_TYPE + COMMA_SEP +
                    StatusReaderContract.StatusEntry.COLUMN_NAME_TEXT+ TEXT_TYPE + " )";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + StatusReaderContract.StatusEntry.TABLE_NAME;

    public StatusReaderDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}