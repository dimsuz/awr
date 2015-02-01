package advaitaworld

import android.support.v7.app.ActionBarActivity
import android.os.Bundle
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import advaitaworld.util.SpaceItemDecoration
import timber.log.Timber
import rx.schedulers.Schedulers
import rx.android.app.AppObservable
import advaitaworld.db.User
import android.view.Menu
import android.view.MenuItem

public class MainActivity : ActionBarActivity() {
    var adapter: PostsAdapter? = null
    val server = Server()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        val postsData = server.getPosts(Section.Popular)
        // at the same time start to fetch user profile data,
        // save it to DB for future reuse and then adapter will also get use of it
        val userData = postsData.flatMapIterable({it -> it}).map({ post ->
            // todo: fetch avatar, create User instance,
            // save it to DB (doOnNext/subscribe), call adapter.onUserInfoUpdated
            User("Mint", "url")
        })
        AppObservable.bindActivity(this, server.getPosts(Section.Popular))
                .subscribeOn(Schedulers.io())
                .subscribe({ list ->
                    adapter?.swapData(list)
                }, {
                    Timber.e(it, "parsing failed with exception")
                })
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