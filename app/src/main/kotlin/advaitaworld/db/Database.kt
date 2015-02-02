package advaitaworld.db

import android.database.sqlite.SQLiteOpenHelper
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import timber.log.Timber
import android.content.ContentValues

private val DATABASE_NAME = "main.db"
private val DATABASE_VERSION = 1


public class Database(context: Context) {
    private val helper: DatabaseHelper = DatabaseHelper(context)

    /**
     * Saves user info to the database.
     * Will throw an exception if saving fails.
     */
    [synchronized]
    public fun saveUser(user: User) {
        val db = helper.getWritableDatabase()
        val values = ContentValues()
        values.put(USERS_COLUMN_NAME, user.name)
        values.put(USERS_COLUMN_AVATAR_URL, user.avatarUrl)
        val id = db.insertOrThrow(USERS_TABLE, null, values)
        Timber.d("wrote $user as $id")
    }

    /**
     * Returns a list of users with matching names.
     * Pass an empty list to get all users.
     */
    [synchronized]
    public fun getUsersByName(names: List<String>) : List<User> {
        val db = helper.getWritableDatabase()
        val cursor = db.query(USERS_TABLE, array(USERS_COLUMN_NAME, USERS_COLUMN_AVATAR_URL),
                "$USERS_COLUMN_NAME IN (${names.join("','", "'", "'")})", null, null, null, null)
        val result : MutableList<User> = arrayListOf()
        val nameIdx = cursor.getColumnIndexOrThrow(USERS_COLUMN_NAME)
        val avatarIdx = cursor.getColumnIndexOrThrow(USERS_COLUMN_AVATAR_URL)
        while(cursor.moveToNext()) {
            result.add(User(cursor.getString(nameIdx), cursor.getString(avatarIdx)))
        }
        return result
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
                "$USERS_COLUMN_NAME TEXT NOT NULL UNIQUE," +
                "$USERS_COLUMN_AVATAR_URL TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, vOld: Int, vNew: Int) {
        Timber.d("upgrading app database from $vOld to $vNew")
    }

}

