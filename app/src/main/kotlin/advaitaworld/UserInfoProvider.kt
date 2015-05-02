package advaitaworld

import advaitaworld.db.Database
import advaitaworld.net.Server
import advaitaworld.parsing.User
import android.content.Context
import rx.Observable
import timber.log.Timber
import kotlin.properties.Delegates

object UserInfoProvider {
    private var db: Database by Delegates.notNull()
    private var server: Server by Delegates.notNull()

    fun initialize(context: Context) {
        val app = AnApplication.get(context)
        db = app.database
        server = app.server
    }
    /**
     * Fetches a full user info either from local DB if available, or by
     * issuing a server info retrieval
     */
    fun getUsersByName(names: List<String>) : Observable<User> {
        return Observable.just(db.getUsersByName(names)).flatMap({ existingUsers ->
            val missingNames = hashSetOf<String>()
            names.filterNotTo(missingNames, { name -> existingUsers.any({ u -> u.name == name }) })
            Timber.d("missing names are: $missingNames\nexisting users: ${existingUsers.size()}")
            val missingNamesFetchers = missingNames.map({ server.getUserInfo(it) })
            Observable.merge(missingNamesFetchers)
                    .doOnNext({ db.saveUser(it) })
                    .startWith(existingUsers)
        })
    }
}

