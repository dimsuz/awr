package advaitaworld

import advaitaworld.net.Section
import advaitaworld.parsing.DefaultMediaResolver
import advaitaworld.util.LoadIndicator
import advaitaworld.util.SpaceItemDecoration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.advaitaworld.widgets.SlidingTabLayout
import rx.Observable
import rx.Subscription
import rx.android.lifecycle.LifecycleEvent
import rx.android.lifecycle.LifecycleObservable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import java.util.EnumMap
import kotlin.properties.Delegates

public class MainActivity : BaseActivity(BaseActivity.Config(R.layout.activity_main)) {
    private var mainPager : ViewPager by Delegates.notNull()
    private val fetchSubscriptions : EnumMap<Section, Subscription> = EnumMap(javaClass<Section>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTabsAndPager()
    }

    private fun setupTabsAndPager() {
        mainPager = findViewById(R.id.main_pager) as ViewPager
        mainPager.setAdapter(MainPagesAdapter(getResources(), lifecycle()))

        val pageListener = createPageChangeListener(mainPager)

        val tabsLayout = findViewById(R.id.tabs) as SlidingTabLayout
        tabsLayout.setSelectedIndicatorColors(getResources().getColor(R.color.accent))
        tabsLayout.setTabTitleColors(Color.WHITE, getResources().getColor(R.color.primary_light))
        tabsLayout.setCustomTabView(R.layout.section_tab, R.id.tab_text_view)
        tabsLayout.setViewPager(mainPager)
        tabsLayout.setOnPageChangeListener(pageListener)
        // ah, the joy... infamous 'no initial page change event' problem...
        mainPager.post { pageListener.onPageSelected(0) }
    }

    private fun createPageChangeListener(viewPager: ViewPager) : ViewPager.OnPageChangeListener {
        return object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
            }

            override fun onPageSelected(position: Int) {
                val viewPagerAdapter = viewPager.getAdapter() as MainPagesAdapter
                val section = Section.values()[position]
                val pageListView = viewPagerAdapter.getPageView(section)
                fetchPosts(pageListView, section)
            }

            override fun onPageScrollStateChanged(state: Int) {
            }
        }
    }

    private fun fetchPosts(pageListView: RecyclerView, section: Section) {
        if(fetchSubscriptions.get(section) != null && !fetchSubscriptions.get(section).isUnsubscribed()) {
            Timber.d("fetching posts for $section is already in progress, not starting new fetch")
            return
        }
        // FIXME if fetching is completed, do not start it
        val server = AnApplication.get(this).server
        val postsData = server.getPosts(section, DefaultMediaResolver(this))
        val adapter = PostFeedAdapter(getResources(), lifecycle())
        setupVoteActions(adapter)
        val subscription = LifecycleObservable.bindUntilLifecycleEvent(lifecycle(), postsData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(LoadIndicator
                        .createFor(postsData)
                        .withErrorText(R.string.error_msg_data_fetch_failed)
                        .withRetryAction(R.string.action_retry, { fetchPosts(pageListView, section) })
                        .showIn(pageListView, adapter))
                .subscribe(
                        { postData ->
                            adapter.swapData(postData)
                            // after saving main data in adapter, start fetching full user info
                            //fetchUserInfo(postData.map({ it.content.author }))
                        },
                        { Timber.e(it, "parsing failed with exception") })
        fetchSubscriptions.put(section, subscription)
    }

    private fun setupVoteActions(adapter: PostFeedAdapter) {
        adapter.setVoteChangeAction { postId, isVoteUp ->
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_refresh) {
            val mainAdapter = mainPager.getAdapter() as MainPagesAdapter
            val section = Section.values()[mainPager.getCurrentItem()]
            val listView = mainAdapter.getPageView(section)
            fetchPosts(listView, section)
            return true
        } else if(id == R.id.action_logout) {
            val profileManager = AnApplication.get(this).profileManager
            val profile = profileManager.getCurrentUserProfile(this)
            if(profile != null) {
                profileManager.logoutUser(this, profile)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                    { Timber.d("logout of $it was success")},
                    { Timber.d(it, "logout failed with error")})
            }
        }

        return super.onOptionsItemSelected(item)
    }

}

private class MainPagesAdapter(val resources: Resources, val activityLifecycle: Observable<LifecycleEvent>) : PagerAdapter() {
    val pageViews : EnumMap<Section, RecyclerView> = EnumMap(javaClass<Section>())

    override fun getCount(): Int {
        return Section.values().size()
    }

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view == obj
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = createFeedListView(container)
        container.addView(view)
        pageViews.put(Section.values()[position], view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        container.removeView(obj as View)
    }

    override fun getPageTitle(position: Int): CharSequence {
        return resources.getString(Section.values()[position].nameResId)
    }

    private fun createFeedListView(container: ViewGroup) : RecyclerView {
        val context = container.getContext()
        val listView = LayoutInflater.from(context).inflate(R.layout.posts_feed_list, container, false) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false))
        listView.addItemDecoration(SpaceItemDecoration(
                context.getResources().getDimensionPixelSize(R.dimen.card_vertical_margin)))
        return listView
    }

    public fun getPageView(section: Section): RecyclerView {
        return pageViews.get(section)!!
    }
}
