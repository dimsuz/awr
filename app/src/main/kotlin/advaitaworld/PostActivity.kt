package advaitaworld

import android.os.Bundle
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import rx.android.lifecycle.LifecycleObservable
import advaitaworld.util.printError
import advaitaworld.support.RxActionBarActivity
import android.view.Menu
import android.support.v7.widget.Toolbar
import advaitaworld.parsing.PostData
import android.widget.TextView
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import advaitaworld.util.SpaceItemDecoration

public class PostActivity : RxActionBarActivity() {
    val server: Server by ServerProvider()
    val adapter: PostAdapter = PostAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        getSupportActionBar().setDisplayShowTitleEnabled(false)
        val postId = getIntent().getStringExtra(EXTRA_POST_ID)
        if(postId == null) {
            throw RuntimeException("post id missing")
        }

        setupPostView()

        LifecycleObservable.bindActivityLifecycle(lifecycle(), server.getFullPost(postId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { showPostData(it) },
                        printError("failed to retrieve fullpost"))
    }

    private fun setupPostView() {
        val listView = findViewById(R.id.post_view) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        listView.setAdapter(adapter)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    private fun showPostData(data: PostData) {
        adapter.swapData(data)
    }
}
