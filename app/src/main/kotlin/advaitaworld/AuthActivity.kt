package advaitaworld

import advaitaworld.support.RxActionBarActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber

public class AuthActivity : RxActionBarActivity() {
    private val server: Server by ServerProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authorization)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        setTitle(R.string.login)
        createMainNavigationDrawer(this)

        findViewById(R.id.auth_button_login).setOnClickListener( {
            val observable = server.loginUser("test", "testing")
            LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), observable, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                { userName -> Timber.d("successfully logged in, username: $userName") },
                { ex -> Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show() })
    }
}
