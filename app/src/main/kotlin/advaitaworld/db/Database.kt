package advaitaworld.db

import android.database.sqlite.SQLiteOpenHelper
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import timber.log.Timber

private val DATABASE_NAME = "main.db"
private val DATABASE_VERSION = 1

private var helper: DatabaseHelper? = null

class DbProvider {
    fun get(context: Context, propertyMetadata: PropertyMetadata): SQLiteDatabase {
        if(helper == null) {
            helper = DatabaseHelper(context.getApplicationContext())
        }
        return helper!!.getWritableDatabase()
    }
}

private class DatabaseHelper(val context : Context)
: SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    {
        Timber.d("creating DB helper")
    }

    override fun onCreate(db: SQLiteDatabase) {
        Timber.d("creating a new database")
        db.execSQL("CREATE TABLE $USERS_TABLE (" +
                "$USERS_COLUMN_ID INTEGER PRIMARY KEY," +
                "$USERS_COLUMN_NAME TEXT NOT NULL," +
                "$USERS_COLUMN_AVATAR_URL TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, vOld: Int, vNew: Int) {
        Timber.d("upgrading app database from $vOld to $vNew")
    }

}

