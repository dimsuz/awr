package advaitaworld

import advaitaworld.support.RxActionBarActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar

public class AuthActivity : RxActionBarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authorization)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        setTitle(R.string.login)
        createMainNavigationDrawer(this)
    }
}
