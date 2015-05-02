package advaitaworld

import advaitaworld.net.Server
import advaitaworld.net.ServerProvider
import advaitaworld.util.*
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.view.Menu
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

public class PostActivity : BaseActivity(BaseActivity.Config(R.layout.activity_post, useNavDrawer = false)) {
    private val adapter: CommentsAdapter = CommentsAdapter(lifecycle(), showPost = true)
    private var postId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getSupportActionBar().setDisplayShowTitleEnabled(false)
        val postIntentId = getIntent().getStringExtra(EXTRA_POST_ID)
        if(postIntentId == null) {
            throw RuntimeException("post id missing")
        }
        postId = postIntentId

        val listView = setupPostView()

        val postData = server.getFullPost(postId)
        LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), postData, LifecycleEvent.DESTROY)
                .compose(LoadIndicator
                        .createFor(postData)
                        .withBackgroundColor(Color.WHITE)
                        .withErrorText(R.string.error_msg_data_fetch_failed)
                        // FIXME implement
                        .withRetryAction(R.string.action_retry, { throw RuntimeException("FIXME implement me")})
                        .showIn(listView, adapter))
                .map { prepareAdapterData(it, null) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { data -> adapter.swapData(data.first, data.second) },
                        printError("failed to retrieve fullpost"))
    }

    private fun setupPostView() : RecyclerView {
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

        return listView
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }
}
