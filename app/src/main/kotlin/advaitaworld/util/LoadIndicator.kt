package advaitaworld.util

import advaitaworld.R
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import rx.Observable
import rx.android.internal.Assertions
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber

/**
 * A transformer which can be composed onto some Observable to provide an convenient
 * way to show a loading indicator (progress bar) until it completes
 */
public class LoadIndicator<T> private (private val container: ViewGroup,
                                       private val retryActionNameResId: Int,
                                       private val errorTextResId: Int,
                                       private val retryAction: (() -> Unit)?,
                                       private val adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>?)
: Observable.Transformer<T,T> {

    companion object {
        /**
         * Starts building a LoadIndicator object.
         *
         * @param observable is used purely to aid in type inference, to avoid the need for
         * the caller to explicitly specify type
         */
        [suppress("UNUSED_PARAMETER")]
        fun <T> createFor(observable: Observable<T>) : Builder<T> {
            return Builder()
        }
    }

    public class Builder<T> internal  () {
        var errorMessageResId = 0
        var retryActionResId = 0
        var retryAction: (() -> Unit)? = null

        /**
         * If source observable produces an error, show this error text
         * (text will be shown above the retry button if it will be set up)
         */
        public fun withErrorText(resId: Int) : Builder<T> {
            errorMessageResId = resId
            return this
        }

        /**
         * Specify the properties of a retry button to show if source observable produces an error.
         * By default no button will be shown.
         */
        public fun withRetryAction(actionNameResId: Int, action: () -> Unit) : Builder<T> {
            retryActionResId = actionNameResId
            retryAction = action
            return this
        }

        /**
         * Specifies the target container to show indicator in.
         * This should probably be a [FrameLayout] instance or some other [ViewGroup].
         * To show load indicator in [RecyclerView], use other overload of showIn
         */
        public fun showIn(container: ViewGroup) : LoadIndicator<T> {
            checkConfiguration()
            return LoadIndicator(container, retryActionResId, errorMessageResId, retryAction, null)
        }

        /**
         * Specifies a target [RecyclerView] to show indicator in.
         * When source observable will start loading data, a new temporary adapter will be
         * set on *listView* for showing a progress bar. When data loading finishes, *listView* will
         * be updated with target adapter passed in *adapter* argument.
         * Client should ensure that this adapter is filled with data (either before onComplete call or later)
         *
         * @param listView a target list view
         * @param adapter an adapter to set on [listView] after source observable completes
         */
        public fun showIn(listView: RecyclerView, adapter: RecyclerView.Adapter<out RecyclerView.ViewHolder>) : LoadIndicator<T> {
            checkConfiguration()
            return LoadIndicator(listView, retryActionResId, errorMessageResId, retryAction, adapter)
        }

        private fun checkConfiguration() {
            // FIXME throw an error if errorMessageResId is not set
        }
    }

    override fun call(observable: Observable<T>): Observable<T> {
        val scheduler = AndroidSchedulers.mainThread()
        val mainThread = scheduler.createWorker()
        // FIXME implement showing an error and retrying
        return observable
                .doOnSubscribe { mainThread.schedule({ showProgress() })}
                .doOnCompleted { mainThread.schedule({ hideProgress() })}
                .finallyDo({ mainThread.schedule({ mainThread.unsubscribe() }) })
    }

    private fun showProgress() {
        Assertions.assertUiThread()
        if(container is RecyclerView) {
            showProgressInRecyclerView(container)
        } else {
            showProgressInViewGroup(container)
        }
    }

    private fun hideProgress() {
        Assertions.assertUiThread()
        if(container is RecyclerView) {
            hideProgressInRecyclerView(container)
        } else {
            hideProgressInViewGroup(container)
        }
    }

    private fun showProgressInViewGroup(viewGroup: ViewGroup) {
        viewGroup.children().forEach { it.setVisible(false) }
        val progressBar = ProgressBar(container.getContext())
        progressBar.setTag("pb")
        viewGroup.addView(progressBar,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    private fun hideProgressInViewGroup(viewGroup: ViewGroup) {
        viewGroup.children().forEach { it.setVisible(true) }
        val progressBar = viewGroup.findViewWithTag("pb")
        if(progressBar != null) {
            viewGroup.removeView(progressBar)
        }
    }

    private fun showProgressInRecyclerView(recyclerView: RecyclerView) {
        recyclerView.setAdapter(WaitAdapter())
    }

    private fun hideProgressInRecyclerView(recyclerView: RecyclerView) {
        // replace a wait adapter with actual adapter which should have loaded data by now
        recyclerView.setAdapter(adapter)
    }

    private class WaitAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wait_progress_bar, parent, false)
            // have to manually update layout params to make item match parent height
            val lp = view.getLayoutParams()
            lp.height = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom()
            view.setLayoutParams(lp)
            return object : RecyclerView.ViewHolder(view) {  }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        }

        override fun getItemCount(): Int {
            return 1
        }
    }

}
