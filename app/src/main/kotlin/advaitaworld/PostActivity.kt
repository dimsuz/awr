package advaitaworld

import android.os.Bundle
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import rx.android.lifecycle.LifecycleObservable
import advaitaworld.util.printError
import advaitaworld.support.RxActionBarActivity
import android.view.Menu
import android.support.v7.widget.Toolbar
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import rx.android.lifecycle.LifecycleEvent
import android.content.Intent
import advaitaworld.util.CommentItemDecoration
import advaitaworld.util.StaircaseItemDecoration
import advaitaworld.util.CommentThreadsDecoration

public class PostActivity : RxActionBarActivity() {
    private val server: Server by ServerProvider()
    private val adapter: CommentsAdapter = CommentsAdapter(lifecycle(), showPost = true)
    private var postId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        getSupportActionBar().setDisplayShowTitleEnabled(false)
        val postIntentId = getIntent().getStringExtra(EXTRA_POST_ID)
        if(postIntentId == null) {
            throw RuntimeException("post id missing")
        }
        postId = postIntentId

        setupPostView()

        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), server.getFullPost(postId), LifecycleEvent.DESTROY)
                .map { prepareAdapterData(it, null) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { data -> adapter.swapData(data.first, data.second) },
                        printError("failed to retrieve fullpost"))
    }

    private fun setupPostView() {
        val listView = findViewById(R.id.post_view) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        listView.addItemDecoration(StaircaseItemDecoration(getResources()))
        listView.addItemDecoration(CommentItemDecoration())
        listView.addItemDecoration(CommentThreadsDecoration(getResources()))
        listView.setAdapter(adapter)

        adapter.setExpandCommentAction { node ->
            val intent = Intent(this, javaClass<CommentsActivity>())
            intent.putExtra(EXTRA_POST_ID, postId)
            intent.putExtra(EXTRA_COMMENT_PATH, node.path)
            startActivity(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }
}
