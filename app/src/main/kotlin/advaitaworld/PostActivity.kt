package advaitaworld

import android.os.Bundle
import advaitaworld.support.RxActionBarActivity
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import rx.android.lifecycle.LifecycleObservable
import advaitaworld.util.printError
import timber.log.Timber

public class PostActivity : RxActionBarActivity() {
    val server: Server by ServerProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("got server {}", server)
        val postId = getIntent().getStringExtra(EXTRA_POST_ID)
        if(postId == null) {
            throw RuntimeException("post id missing")
        }
        LifecycleObservable.bindActivityLifecycle(lifecycle(), server.getFullPost(postId))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { Timber.d("got full post") },
                        printError("failed to retrieve fullpost"))
    }
}
