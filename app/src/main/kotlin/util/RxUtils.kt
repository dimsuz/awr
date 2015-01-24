package com.advaitaworld.app.util

import rx.Subscriber
import rx.Observable

public fun <T> runOnce(body: () -> T) : Observable<T> {
    return Observable.create({ subscriber ->
        // need to wrap in extra fun because Kotlin does not allow local returns from lambdas
        runBodyOnce(body, subscriber)
    })
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

