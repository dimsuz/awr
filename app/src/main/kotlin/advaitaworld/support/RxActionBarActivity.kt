package advaitaworld.support

/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import rx.Observable
import rx.android.lifecycle.LifecycleEvent
import rx.subjects.BehaviorSubject

// NOTE: this is the copy of RxActivity.java from rx-framework which
// extends ActionBarActivity - this is currently missing from rx-android-framework
// (search RxAndroid github for ActionBarActivity to find related issues)
/**
 * An Activity with reactive extensions.
 */
public open class RxActionBarActivity : AppCompatActivity() {
    private val lifecycleSubject = BehaviorSubject.create<LifecycleEvent>()

    public fun lifecycle(): Observable<LifecycleEvent> {
        return lifecycleSubject.asObservable()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleSubject.onNext(LifecycleEvent.CREATE)
    }

    override fun onStart() {
        super.onStart()
        lifecycleSubject.onNext(LifecycleEvent.START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleSubject.onNext(LifecycleEvent.RESUME)
    }

    override fun onPause() {
        lifecycleSubject.onNext(LifecycleEvent.PAUSE)
        super.onPause()
    }

    override fun onStop() {
        lifecycleSubject.onNext(LifecycleEvent.STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleSubject.onNext(LifecycleEvent.DESTROY)
        super.onDestroy()
    }
}
