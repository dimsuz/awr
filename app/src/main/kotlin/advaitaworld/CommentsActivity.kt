package advaitaworld

import advaitaworld.support.RxActionBarActivity
import android.os.Bundle
import android.support.v7.widget.Toolbar
import rx.android.lifecycle.LifecycleObservable
import rx.android.lifecycle.LifecycleEvent
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.LinearLayoutManager
import advaitaworld.util.DividerItemDecoration
import advaitaworld.parsing.findByPath
import advaitaworld.parsing.PostData

public class CommentsActivity : RxActionBarActivity() {
    private val server: Server by ServerProvider()
    private val adapter: CommentsAdapter = CommentsAdapter(showPost = false)
    private var postId = ""
    private var rootPostData: PostData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comments)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        getSupportActionBar().setDisplayHomeAsUpEnabled(true)
        getSupportActionBar().setTitle("Комментарии")

        postId = getIntent().getStringExtra(EXTRA_POST_ID) ?: ""
        val commentPath = getIntent().getLongArrayExtra(EXTRA_COMMENT_PATH)
        if(postId.isEmpty()) throw RuntimeException("post id missing")
        if(commentPath == null) throw RuntimeException("comment path is missing")

        setupCommentsView()

        val observable = server.getFullPost(postId)
                .doOnNext { rootPostData = it }
                .map { it.limitToNode(commentPath) }

        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), observable, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { adapter.swapData(it) },
                        { Timber.e(it, "failed to retrieve fullpost") })
    }

    private fun setupCommentsView() {
        val listView = findViewById(R.id.post_view) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        val dividerDecor = DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST)
        listView.addItemDecoration(dividerDecor)
        listView.setAdapter(adapter)

        adapter.setExpandCommentAction { node ->
            val data = rootPostData
            if(data != null) {
                adapter.swapData(data.limitToNode(node.path))
            }
        }
    }
}

/**
 * Returns a copy of PostData which has only comments starting with the one pointed by path
 */
private fun PostData.limitToNode(path: LongArray): PostData {
    val targetComment = this.comments.stream()
            .map { it.findByPath(path) }
            .filter { it != null }
            .take(1)
            .first()
    return PostData(this.content, arrayListOf(targetComment!!))
}
