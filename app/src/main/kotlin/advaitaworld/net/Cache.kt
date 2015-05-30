package advaitaworld.net

import advaitaworld.parsing.PostData
import android.util.SparseArray
import rx.Observable
import timber.log.Timber

public interface Cache {
    /**
     * Returns an observable which will either emit a post data retrieved from cache or will call
     * <em>onError</em> if no data for this post is cached
     */
    public fun getFullPost(postId: String) : Observable<PostData>

    /**
     * Asynchronously saves a post to cache, returns an observable which will emit a postId when
     * saving finishes or an error if saving fails
     */
    public fun saveFullPost(postId: String, data: PostData) : Observable<String>

    /**
     * Clears cache
     */
    public fun clear()
}

public class MemoryCache : Cache {
    // FIXME introduce some cache limits to prevent it from growing too large (use ArrayDeque?)
    private val cache: SparseArray<PostData> = SparseArray()

    override fun getFullPost(postId: String): Observable<PostData> {
        val data = cache.get(postId.hashCode())
        return if(data != null) Observable.just(data) else Observable.error(RuntimeException("cache miss"))
    }

    override fun saveFullPost(postId: String, data: PostData) : Observable<String> {
        cache.put(postId.hashCode(), data)
        return Observable.just(postId)
    }

    override fun clear() {
        Timber.d("clearing requests cache")
        cache.clear()
    }
}
