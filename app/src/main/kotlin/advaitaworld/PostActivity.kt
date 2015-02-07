package advaitaworld

import android.os.Bundle
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import rx.android.lifecycle.LifecycleObservable
import advaitaworld.util.printError
import timber.log.Timber
import advaitaworld.support.RxActionBarActivity
import android.view.Menu
import android.support.v7.widget.Toolbar
import advaitaworld.parsing.PostData
import android.widget.TextView

public class PostActivity : RxActionBarActivity() {
    val server: Server by ServerProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)
        setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)
        getSupportActionBar().setDisplayShowTitleEnabled(false)
        val postId = getIntent().getStringExtra(EXTRA_POST_ID)
        if(postId == null) {
            throw RuntimeException("post id missing")
        }
        Timber.d("start full post getter")
        LifecycleObservable.bindActivityLifecycle(lifecycle(), server.getFullPost(postId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { showPostData(it) },
                        printError("failed to retrieve fullpost"))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    private fun showPostData(data: PostData) {
        val titleView = findViewById(R.id.post_title) as TextView
        val subtitleView = findViewById(R.id.post_subtitle) as TextView
        val contentView = findViewById(R.id.post_content) as TextView
        titleView.setText("Вот те раз!")
        subtitleView.setText(data.content.author)
        contentView.setText(data.content.text)
    }
}
