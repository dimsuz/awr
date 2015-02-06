package advaitaworld

import android.os.Bundle
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import rx.android.lifecycle.LifecycleObservable
import advaitaworld.util.printError
import timber.log.Timber
import advaitaworld.support.RxActionBarActivity

public class PostActivity : RxActionBarActivity() {
    val server: Server by ServerProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post)
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
