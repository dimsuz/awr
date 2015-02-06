package advaitaworld.util

import rx.Subscriber
import rx.Observable
import timber.log.Timber
import android.os.Looper
import advaitaworld.BuildConfig

public fun <T> runOnce(body: () -> T) : Observable<T> {
    var observable = Observable.create<T>({ subscriber ->
        // need to wrap in extra fun because Kotlin does not allow local returns from lambdas
        runBodyOnce(body, subscriber)
    })
    if(BuildConfig.DEBUG) {
        observable = observable.doOnSubscribe({
            if (Looper.myLooper() == Looper.getMainLooper()) {
                Timber.e("note: task will be running on ui thread! this might be an error")
            }
        })
    }
    return observable
}

private fun <T> runBodyOnce(body: () -> T, subscriber: Subscriber<T>?) {
    if(subscriber == null || subscriber.isUnsubscribed()) {
        return
    }
    try {
        val result = body()
        if (!subscriber.isUnsubscribed()) {
            subscriber.onNext(result)
            subscriber.onCompleted()
        }
    } catch(e: Throwable) {
        if(!subscriber.isUnsubscribed()) {
            subscriber.onError(e)
        }
    }
}

public fun printError(message: String) : (Throwable) -> Unit {
    return { e -> Timber.e(message, e) }
}
