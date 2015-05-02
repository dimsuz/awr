package advaitaworld

import advaitaworld.support.RxActionBarActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar

public open class BaseActivity(private val config: BaseActivity.Config) : RxActionBarActivity() {
    data class Config(val contentLayoutId: Int, val useNavDrawer: Boolean = true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(config.contentLayoutId)
        val toolbar = findViewById(R.id.toolbar)
        if(toolbar == null) throw RuntimeException("BaseActivity subclass must have a Toolbar with id=toolbar")
        setSupportActionBar(toolbar as Toolbar)
        if(config.useNavDrawer) {
            val profileManager = AnApplication.get(this).profileManager
            createMainNavigationDrawer(this, profileManager.getCurrentUserProfile(this))
        }
    }
}
