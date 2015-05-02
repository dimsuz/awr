package advaitaworld.auth

import advaitaworld.*
import advaitaworld.support.RxActionBarActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.widget.EditText
import android.widget.Toast
import com.dd.CircularProgressButton
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.android.view.ViewObservable
import rx.schedulers.Schedulers
import timber.log.Timber
import kotlin.properties.Delegates

public class AuthActivity : RxActionBarActivity() {
    private val server: advaitaworld.net.Server by advaitaworld.net.ServerProvider()
    private val profileManager = ProfileManager(server)

    private var loginEdit: EditText by Delegates.notNull()
    private var passwordEdit: EditText by Delegates.notNull()
    private var loginButton: CircularProgressButton by Delegates.notNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authorization)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        setTitle(R.string.login)
        createMainNavigationDrawer(this, profileManager.getCurrentUserProfile(this))

        loginEdit = findViewById(R.id.auth_login_edit) as EditText
        passwordEdit = findViewById(R.id.auth_password_edit) as EditText
        loginButton = findViewById(R.id.auth_button_login) as CircularProgressButton
        loginButton.setIndeterminateProgressMode(true)

        val loginClicks = ViewObservable.clicks(loginButton)
            .map { loginEdit.getText().length() != 0 && passwordEdit.getText().length() != 0 }

        LifecycleObservable.bindActivityLifecycle(lifecycle(), loginClicks)
        .subscribe { haveValidInfo ->
            if(haveValidInfo) {
                startLogin(loginEdit.getText().toString(), passwordEdit.getText().toString())
            } else {
                Toast.makeText(this, R.string.auth_error_missing_credentials, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startLogin(login: String, password: String) {
        loginButton.setProgress(50) // will show progress bar
        val observable = profileManager.loginUser(this, login, password)
        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), observable, LifecycleEvent.DESTROY)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { userName ->
                    Timber.d("successfully logged in, username: $userName")
                    // show 'ok' for some time, then finish
                    loginButton.setProgress(100)
                    loginButton.postDelayed({ finish() }, 800)
                },
                { ex ->
                    // FIXME display this not in toast, but permanently somewhere in layout
                    Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show()
                    // show error for some time, then reset to default state (for retry)
                    loginButton.setProgress(-1)
                    loginButton.postDelayed({ loginButton.setProgress(0) }, 1200)
                })
    }
}