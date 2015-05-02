package advaitaworld

import advaitaworld.auth.ProfileManager
import advaitaworld.db.Database
import advaitaworld.net.MemoryCache
import advaitaworld.net.Server
import android.app.Application
import android.content.Context
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import timber.log.Timber
import kotlin.platform.platformStatic
import kotlin.properties.Delegates

public class AnApplication : Application() {
    public var server : Server by Delegates.notNull()
        private set
    public var database : Database by Delegates.notNull()
        private set
    public var profileManager : ProfileManager by Delegates.notNull()
        private set

    companion object {
        [platformStatic]
        fun get(context: Context) : AnApplication {
            return context.getApplicationContext() as AnApplication
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        UserInfoProvider.initialize(this)
        DrawerImageLoader.init(createDrawerImageLoader())

        // Using this until there will be some proper DI for Kotlin...
        createGlobalObjects()
    }

    private fun createGlobalObjects() {
        server = Server(this, MemoryCache())
        database = Database(this)
        profileManager = ProfileManager(server)
    }
}
