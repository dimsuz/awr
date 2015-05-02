package advaitaworld

import android.app.Application
import com.mikepenz.materialdrawer.util.DrawerImageLoader
import timber.log.Timber

public class AnApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        UserInfoProvider.initialize(this)
        DrawerImageLoader.init(createDrawerImageLoader())
    }
}
