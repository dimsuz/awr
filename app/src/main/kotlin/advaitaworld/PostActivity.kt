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
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import advaitaworld.util.DividerItemDecoration
import timber.log.Timber
import rx.android.lifecycle.LifecycleEvent
import android.content.Intent

public class PostActivity : RxActionBarActivity() {
    private val server: Server by ServerProvider()
    private val adapter: CommentsAdapter = CommentsAdapter(showPost = true)
    private var postId = ""

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

        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), server.getFullPost(postId), LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { showPostData(it) },
                        printError("failed to retrieve fullpost"))
    }

    private fun setupPostView() {
        val listView = findViewById(R.id.post_view) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        val dividerDecor = DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST)
        dividerDecor.setStartItem(1) // first item is the post itself, doesn't need a divider
        listView.addItemDecoration(dividerDecor)
        listView.setAdapter(adapter)

        adapter.setExpandCommentAction { node ->
            val intent = Intent(this, javaClass<CommentsActivity>())
            intent.putExtra(EXTRA_POST_ID, postId)
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    private fun showPostData(data: PostData) {
        Timber.d("showing post with ${data.comments.size()} top level comments")
        adapter.swapData(data)
    }
}
