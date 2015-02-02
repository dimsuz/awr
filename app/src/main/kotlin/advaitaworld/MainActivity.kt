package advaitaworld

import android.support.v7.app.ActionBarActivity
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import advaitaworld.util.SpaceItemDecoration
import timber.log.Timber
import rx.schedulers.Schedulers
import rx.android.app.AppObservable
import advaitaworld.db.Database
import rx.Observable
import android.view.Menu
import android.view.MenuItem

public class MainActivity : ActionBarActivity() {
    var adapter: PostsAdapter? = null
    val server = Server()
    var db: Database? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Database(getApplicationContext())
        setContentView(R.layout.activity_main)
        setupPostsList()
        fetchPosts()
    }

    private fun setupPostsList() {
        val listView = findViewById(R.id.post_list) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        listView.setHasFixedSize(true)
        listView.addItemDecoration(SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_vertical_margin)))
        adapter = PostsAdapter()
        listView.setAdapter(adapter)
    }

    private fun fetchPosts() {
        MOCK_PAGE_HTML = getAssets().open("main_test.html")
        // Get posts, then find out which authors already have user info record in
        // DB and which haven't. Pass the former to the adapter and fetch info for the latter from
        // server
        val postsData = server.getPosts(Section.Popular)
        AppObservable.bindActivity(this, postsData)
                .subscribeOn(Schedulers.io())
                .map({ posts ->
                    val names = posts.map { it.author }
                    val users = db?.getUsersByName(names) ?: listOf()
                    val missingNames = names.filterNot { name -> users.any({ u -> u.name == name }) }
                    Timber.d("got ${users.size()} from db")
                    Timber.d("missing from db $missingNames")
                    Triple(posts, users, missingNames)
                })
                .doOnNext({ data -> fetchUserInfo(data.third) })
                .subscribe(
                        { adapter?.swapData(it.first, it.second) },
                        { Timber.e(it, "parsing failed with exception") })
    }

    private fun fetchUserInfo(missingNames: List<String>) {
        Timber.d("fetching info for users $missingNames")
        val userData = Observable.from(missingNames)
                .flatMap({ server.getUserInfo(it) })
        AppObservable.bindActivity(this, userData)
                .subscribeOn(Schedulers.io())
                .doOnNext({ if(it.name != "Amin" && it.name != "veter") db?.saveUser(it) })
                .subscribe(
                        { adapter?.onUserInfoUpdated(it) },
                        { Timber.e(it, "failed to retrieve a user avatar") })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}