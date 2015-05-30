package advaitaworld

import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.PublishSubject

public enum class Events(private val subject : PublishSubject<Any>) {
    UserLogin(PublishSubject.create()),
    UserLogout(PublishSubject.create());

    public fun fire() {
        subject.onNext(true)
    }

    public fun toObservable() : Observable<Any> {
        return subject.subscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread())
    }
}
