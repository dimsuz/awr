package advaitaworld

import rx.Observable
import advaitaworld.parsing.User
import android.content.Context
import advaitaworld.db.Database
import timber.log.Timber

object UserInfoProvider {
    private var db: Database? = null
    private var server: Server? = null

    fun initialize(context: Context) {
        db = Database(context)
        server = ServerProvider().get(context)
    }
    /**
     * Fetches a full user info either from local DB if available, or by
     * issuing a server info retrieval
     */
    fun getUsersByName(names: List<String>) : Observable<User> {
        return Observable.just(db!!.getUsersByName(names)).flatMap({ existingUsers ->
            val missingNames = hashSetOf<String>()
            names.filterNotTo(missingNames, { name -> existingUsers.any({ u -> u.name == name }) })
            Timber.d("missing names are: $missingNames\nexisting users: ${existingUsers.size()}")
            val missingNamesFetchers = missingNames.map({ server!!.getUserInfo(it) })
            Observable.merge(missingNamesFetchers)
                    // TODO check that it saves only missing ones and not ones started with
                    .doOnNext({ db!!.saveUser(it) })
                    .startWith(existingUsers)
        })
    }
}
