package advaitaworld

import advaitaworld.parsing.CommentNode
import advaitaworld.parsing.PostData
import advaitaworld.parsing.emptyContentInfo
import advaitaworld.parsing.findByPath
import advaitaworld.support.RxActionBarActivity
import advaitaworld.util.CommentItemDecoration
import advaitaworld.util.CommentThreadsDecoration
import advaitaworld.util.LoadIndicator
import advaitaworld.util.StaircaseItemDecoration
import android.os.Bundle
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.util.ArrayDeque

public class CommentsActivity : RxActionBarActivity() {
    private val server: Server by ServerProvider()

    private val adapter: CommentsAdapter = CommentsAdapter(lifecycle(), showPost = false)
    private var postId = ""
    private var rootPostData: PostData? = null
    // instantiate item animator beforehand so setting it would be quicker
    private val itemAnimator = DefaultItemAnimator()
    private val navHistory = ArrayDeque<LongArray>()
    private var layoutManager: LinearLayoutManager? = null
    private var currentPath: LongArray = longArray()

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

        val listView = setupCommentsView()

        val observable = server.getFullPost(postId)
                .doOnNext { rootPostData = it }

        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), observable, LifecycleEvent.DESTROY)
                .compose(LoadIndicator
                        .createFor(observable)
                        .withErrorText(R.string.error_msg_data_fetch_failed)
                        // FIXME implement
                        .withRetryAction(R.string.action_retry, { throw RuntimeException("FIXME implement me")})
                        .showIn(listView, adapter))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { data -> navigateToPath(commentPath) },
                        { Timber.e(it, "failed to retrieve fullpost") })
    }

    private fun setupCommentsView() : RecyclerView {
        val listView = findViewById(R.id.post_view) as RecyclerView
        layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        listView.setLayoutManager(layoutManager)
        listView.addItemDecoration(StaircaseItemDecoration(getResources()))
        listView.addItemDecoration(CommentItemDecoration())
        listView.addItemDecoration(CommentThreadsDecoration(getResources()))
        // disable animations until the first comment expand (so that first swap will be instant)
        listView.setItemAnimator(null)
        listView.setAdapter(adapter)

        adapter.setExpandCommentAction { node ->
            if(listView.getItemAnimator() == null) {
                listView.setItemAnimator(itemAnimator)
            }
            navHistory.addLast(currentPath)
            navigateToPath(node.path)
        }

        return listView
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.getItemId()) {
            R.id.home, android.R.id.home -> {
                if(navHistory.isNotEmpty()) {
                    navigateToPath(navHistory.removeLast())
                } else {
                    finish()
                }
                return true
            }
        }
        return false
    }

    private fun navigateToPath(path: LongArray) {
        Timber.d("navigating to path ${path.toList()}")
        val data = rootPostData
        if(data != null) {
            layoutManager!!.scrollToPosition(0)
            val (postData, items) = prepareAdapterData(data, path)
            adapter.swapData(postData, items)
            currentPath = path
        }
    }
}

/**
 * Takes a post data and an optional starting comment path and returns a plain list
 * of item indent information suitable for passing to a CommentsAdapter
 */
fun prepareAdapterData(postData: PostData, startPath: LongArray?) : Pair<PostData, List<ItemInfo>> {
    // build indented comment tree by adding a fake root on top, this will provide
    // the right structure, then discard this extra root from results

    // If comment path is provided, no need to add a fake root, because tree will already have a single root
    var layout : List<ItemInfo>
    if(startPath == null) {
        val fakeRoot = CommentNode(longArray(-1), emptyContentInfo(), postData.comments, 0)
        layout = buildTreeLayout(fakeRoot, startIndent = -1).drop(1)
    } else {
        layout = buildTreeLayout(postData.limitToNode(startPath).comments.single())
    }

    return Pair(postData, layout)
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
    return PostData(this.title, this.content, arrayListOf(targetComment!!))
}
