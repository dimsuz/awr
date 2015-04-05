package advaitaworld.util

import advaitaworld.R
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import rx.Observable
import rx.android.internal.Assertions
import rx.android.schedulers.AndroidSchedulers

/**
 * A transformer which can be composed onto some Observable to provide an convenient
 * way to show a loading indicator (progress bar) until it completes
 */
public class LoadIndicator<T> private (private val container: ViewGroup,
                                       private val config: LoadIndicator.Config,
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

    internal data class Config(
        val errorMessageResId : Int,
        val retryActionResId : Int,
        val retryAction: (() -> Unit)?,
        val background: Drawable?
    )

    public class Builder<T> internal  () {
        var errorMessageResId = 0
        var retryActionResId = 0
        var retryAction: (() -> Unit)? = null
        var backgroundDrawable: Drawable? = null

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
         * Specify the background color to use on indicator layout view.
         * Default is to use no color (transparent)
         */
        public fun withBackgroundColor(color: Int) : Builder<T> {
            backgroundDrawable = ColorDrawable(color)
            return this
        }

        /**
         * Specifies the target container to show indicator in.
         * This should probably be a [FrameLayout] instance or some other [ViewGroup].
         * To show load indicator in [RecyclerView], use other overload of showIn
         */
        public fun showIn(container: ViewGroup) : LoadIndicator<T> {
            checkConfiguration()
            return LoadIndicator(container,
                    Config(errorMessageResId, retryActionResId, retryAction, backgroundDrawable),
                    null)
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
            return LoadIndicator(listView,
                    Config(errorMessageResId, retryActionResId, retryAction, backgroundDrawable),
                    adapter)
        }

        private fun checkConfiguration() {
            if(errorMessageResId == 0) {
                throw IllegalStateException("load indicator: error message not specified")
            }
            if(retryAction != null && retryActionResId == 0) {
                throw IllegalStateException("load indicator: retry action is provided, but no action name string resource specified")
            }
        }
    }

    override fun call(observable: Observable<T>): Observable<T> {
        val scheduler = AndroidSchedulers.mainThread()
        val mainThread = scheduler.createWorker()
        // FIXME implement showing an error and retrying
        return observable
                .doOnSubscribe { mainThread.schedule({ showProgress() })}
                .doOnCompleted { mainThread.schedule({ hideProgress() })}
                .doOnError { mainThread.schedule({ hideProgress(); showError() })}
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

    private fun showError() {
        Assertions.assertUiThread()
        if(container is RecyclerView) {
            showErrorInRecyclerView(container)
        } else {
            showErrorInViewGroup(container)
        }
    }

    private fun showProgressInViewGroup(viewGroup: ViewGroup) {
        viewGroup.children().forEach { it.setVisible(false) }
        val loadingView = prepareLoadingView(viewGroup, config)
        viewGroup.addView(loadingView)
    }

    private fun hideProgressInViewGroup(viewGroup: ViewGroup) {
        // FIXME instead of showing *all*, show only those which were visible initally
        // (that requires remembering children state in showProgressInViewGroup)
        viewGroup.children().forEach { it.setVisible(true) }
        val loadingView = viewGroup.findViewById(R.id.load_indicator_progress)
        if(loadingView != null) {
            viewGroup.removeView(loadingView)
        }
    }

    private fun showErrorInViewGroup(viewGroup: ViewGroup) {
        // FIXME implement
    }

    private fun showProgressInRecyclerView(recyclerView: RecyclerView) {
        recyclerView.setAdapter(SingleItemAdapter(prepareLoadingView(recyclerView, config)))
    }

    private fun hideProgressInRecyclerView(recyclerView: RecyclerView) {
        // replace a wait adapter with actual adapter which should have loaded data by now
        recyclerView.setAdapter(adapter)
    }

    private fun showErrorInRecyclerView(recyclerView: RecyclerView) {
        recyclerView.setAdapter(SingleItemAdapter(prepareErrorView(recyclerView, config)))
    }

    private class SingleItemAdapter(val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            // make this single item full sized
            stretchToRecyclerViewSize(parent, view)
            return object : RecyclerView.ViewHolder(view) {  }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        }

        override fun getItemCount(): Int {
            return 1
        }
    }
}

private fun prepareLoadingView(parent: ViewGroup, config: LoadIndicator.Config) : View {
    val loadingView = LayoutInflater.from(parent.getContext()).inflate(R.layout.load_indicator_progress, parent, false)
    if(config.background != null) {
        loadingView.setBackgroundDrawable(config.background)
    }
    return loadingView
}

private fun prepareErrorView(parent: ViewGroup, config: LoadIndicator.Config) : View {
    val errorViewLayout = LayoutInflater.from(parent.getContext()).inflate(R.layout.load_indicator_error, parent, false)
    if(config.background != null) {
        errorViewLayout.setBackgroundDrawable(config.background)
    }

    val errorMsg = errorViewLayout.findViewById(R.id.error_msg) as TextView
    val button = errorViewLayout.findViewById(R.id.retry_button) as TextView

    // expecting all resources are validated by Builder and present
    errorMsg.setText(config.errorMessageResId)
    val buttonAction = config.retryAction
    if(buttonAction != null) {
        button.setText(config.retryActionResId)
        button.setOnClickListener { buttonAction() }
    } else {
        button.setVisible(false)
    }
    return errorViewLayout
}

private fun stretchToRecyclerViewSize(parent: View, view: View) {
    // have to manually update layout params to make adapter item match parent height
    val lp = view.getLayoutParams()
    lp.height = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom()
    view.setLayoutParams(lp)
}

