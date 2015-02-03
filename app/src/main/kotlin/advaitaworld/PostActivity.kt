package advaitaworld

import android.support.v7.app.ActionBarActivity
import android.os.Bundle
import timber.log.Timber

public class PostActivity : ActionBarActivity() {
    val server: Server by ServerProvider()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("got server {}", server)
        val postId = getIntent().getStringExtra(EXTRA_POST_ID)
        if(postId == null) {
            throw RuntimeException("post id missing")
        }
    }
}
