package com.binarypoet.sentiment.db;

import android.provider.BaseColumns;

public final class StatusReaderContract {
    // To prevent someone from accidentally instantiating the contract class,
    // make the constructor private.
    private StatusReaderContract() {}

    /* Inner class that defines the table contents */
    public static class StatusEntry implements BaseColumns {
        public static final String TABLE_NAME = "status";
        public static final String COLUMN_NAME_ID = "id";
        public static final String COLUMN_NAME_TEXT = "text";
        public static final String COLUMN_NAME_SCORE = "score";
    }
}
