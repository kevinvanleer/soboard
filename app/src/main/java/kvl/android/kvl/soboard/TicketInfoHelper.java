package kvl.android.kvl.soboard;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by kvl on 10/20/16.
 */
public class TicketInfoHelper extends SQLiteOpenHelper {
    private static final String LOG_TAG = "TicketInfoHelper";
    private boolean newDb = false;

    TicketInfoHelper(Context context) {
        super(context, DatabaseSchema.DATABASE_NAME, null, DatabaseSchema.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.v(LOG_TAG, "Creating TicketInfo table");
        newDb = true;
        db.execSQL(DatabaseSchema.TicketInfo.TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public boolean isNewDb() {
        return newDb;
    }
}
